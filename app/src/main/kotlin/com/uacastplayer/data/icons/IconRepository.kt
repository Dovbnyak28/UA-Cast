package com.uacastplayer.data.icons

import android.content.Context
import androidx.collection.LruCache
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.icons.CastArtworkPolicy
import com.uacastplayer.icons.IconCandidate
import com.uacastplayer.icons.IconFailurePolicy
import com.uacastplayer.icons.IconMemoryCacheKey
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.icons.ImageFormatDetector
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.log.AppLog
import com.uacastplayer.log.RepeatedNoteFilter
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Wraps a resolved icon [File] so a *negative* result (channel has no icon) can be cached in
 * [IconRepository.memoryCache] too - [LruCache.get] returning null is otherwise indistinguishable
 * from a cache miss. */
private data class CachedIcon(val file: File?)

/**
 * Resolves a channel's icon through the tvg-logo -> EPG-icon -> CDN-by-tvg-id priority chain,
 * consulting/populating [IconDiskCache] and [IconFailureStore] along the way. The CDN fallback is
 * cache-only by design (see [IconCandidate.CacheOnly]) - it is never speculatively fetched.
 *
 * [memoryCache] sits in front of all of that: [IconDiskCache] still hits actual disk I/O on every
 * call, and a channel with no resolvable icon would otherwise repeat the full candidate chain
 * (including disk lookups) on every scroll-triggered recomposition. It's invalidated wholesale by
 * [invalidateMemoryCache] whenever the on-disk picture can have changed underneath it (icon cache
 * cleared, prefetch finished writing new files) rather than tracked per-entry.
 */
class IconRepository(context: Context) {

    private val appContext = context.applicationContext
    private val diskCache = IconDiskCache(appContext)
    private val failureStore = IconFailureStore(appContext)
    private val customSourceStore = CustomIconSourceStore(appContext)
    private val memoryCache = LruCache<String, CachedIcon>(MEMORY_CACHE_SIZE)
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)

    /** Keeps [castArtworkUrl]'s verdict from being written down once a second - see the call site. */
    private val castArtworkNotes = RepeatedNoteFilter()

    // customSourceStore.getBaseUrls() re-reads SharedPreferences AND re-parses a JSON array on
    // every call, and the resolve path below needs it once per channel - i.e. once per list row
    // scrolled into view, and 300 times in a row during a prefetch pass. This list only ever
    // changes through add/removeCustomIconSource (a Settings action), so it's read once and held
    // until one of those invalidates it. @Volatile because resolve runs on Dispatchers.IO while
    // the two mutators are called from the main thread.
    @Volatile private var cachedCustomBaseUrls: List<String>? = null

    private fun customBaseUrls(): List<String> =
        cachedCustomBaseUrls ?: customSourceStore.getBaseUrls().also { cachedCustomBaseUrls = it }

    fun customIconSources(): List<String> = customBaseUrls()

    fun addCustomIconSource(baseUrl: String) {
        val current = customBaseUrls()
        if (baseUrl !in current) {
            customSourceStore.saveBaseUrls(current + baseUrl)
            cachedCustomBaseUrls = null
        }
    }

    fun removeCustomIconSource(baseUrl: String) {
        customSourceStore.saveBaseUrls(customBaseUrls() - baseUrl)
        cachedCustomBaseUrls = null
    }

    /** Drops every entry, positive and negative - see the class doc for when this needs calling. */
    fun invalidateMemoryCache() {
        memoryCache.evictAll()
    }

    suspend fun resolveIconFile(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): File? {
        val cacheKey = IconMemoryCacheKey.of(tvgLogo, epgIconUrl, tvgId)
        memoryCache.get(cacheKey)?.let { return it.file }

        val resolved = resolveIconFileUncached(tvgLogo, epgIconUrl, tvgId)
        memoryCache.put(cacheKey, CachedIcon(resolved))
        return resolved
    }

    /**
     * Dispatched to IO as a whole, not per disk lookup. Both callers reach this from the main
     * thread - [com.uacastplayer.ui.components.ChannelIcon]'s produceState runs on the composition
     * dispatcher, and IconController's prefetch job runs on `viewModelScope` (Dispatchers.Main) -
     * and the per-candidate work here is not free: a SHA-256 fingerprint per candidate URL (see
     * [IconDiskCache] / [IconFailureStore]) plus the candidate-chain construction itself, once per
     * channel. Leaving that on the main thread made a prefetch pass (up to 300 channels back to
     * back, right after a playlist load) compete with rendering for the exact frames the user is
     * scrolling through. One hop out here also replaces the N separate hops [IconDiskCache.get]
     * used to make per candidate.
     */
    private suspend fun resolveIconFileUncached(
        tvgLogo: String?,
        epgIconUrl: String?,
        tvgId: String?,
    ): File? = withContext(Dispatchers.IO) {
        val candidates = IconResolver.candidates(
            tvgLogo, epgIconUrl, tvgId,
            customBaseUrls = customBaseUrls(),
            cdnFallbackUrl = ::cdnFallbackUrl,
        )
        for (candidate in candidates) {
            diskCache.get(candidate.url)?.let { return@withContext it }
            if (candidate is IconCandidate.CacheOnly) continue
            if (failureStore.shouldSkip(candidate.url)) continue

            val fetched = fetchAndValidate(candidate.url)
            if (fetched != null) return@withContext fetched
        }
        null
    }

    /**
     * The URL a Cast receiver should be given as artwork for this channel, picked out of the same
     * candidate chain [resolveIconFile] walks - see [CastArtworkPolicy] for why it is not simply
     * the first candidate.
     *
     * Unlike its two neighbours this touches neither disk nor network: it builds the chain and
     * picks a URL out of it, so it is safe to call straight from the main thread while switching
     * channels. The receiver does its own fetching.
     */
    fun castArtworkUrl(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): String? {
        val candidates = IconResolver.candidates(
            tvgLogo, epgIconUrl, tvgId,
            customBaseUrls = customBaseUrls(),
            cdnFallbackUrl = ::cdnFallbackUrl,
        )
        val url = CastArtworkPolicy.artworkUrl(candidates)
        // `cast load: artwork=false` in CastSessionRepository says a receiver got no picture; it
        // cannot say why, and the two reasons want opposite fixes. A playlist entry with no tvg-id
        // at all is the provider's doing and nothing here can help. An entry that reaches only the
        // cache-only CDN guess is *this policy* declining a URL the phone may well be displaying
        // from disk right now - see CastArtworkPolicy's last paragraph. Never the url itself: this
        // ends up in a shared diagnostics report.
        // Only when the answer changes. This is called on every cast metadata build - about once a
        // second while a channel is playing - and a channel with no logo answers identically every
        // time, so writing it each time filled the whole diagnostics buffer with one sentence. See
        // RepeatedNoteFilter for the report where that was measured.
        if (url == null) {
            val note = "cast artwork: none, candidates=${candidates.size} (fetchable=0)"
            if (castArtworkNotes.isWorthLogging(note)) AppLog.d(TAG) { note }
        }
        return url
    }

    suspend fun trimCache() = diskCache.trim()

    /** See [IconFailureStore.pruneExpiredFailures] - call once per playlist load/refresh. */
    fun pruneExpiredFailures() = failureStore.pruneExpiredFailures()

    private fun cdnFallbackUrl(tvgId: String): String =
        IconResolver.iconUrl(IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL, tvgId)

    // The IOException itself is never surfaced beyond marking this URL as a transient failure
    // (see IconFailurePolicy) - there's nothing about a network/read error worth logging per icon.
    @Suppress("SwallowedException")
    private suspend fun fetchAndValidate(url: String): File? = withContext(Dispatchers.IO) {
        try {
            // Many icon hosts have hotlink protection that 403s the default OkHttp UA (which
            // identifies itself as "okhttp/<version>") - a browser-looking UA gets through the
            // same check a real browser would pass. See HttpDefaults for why this constant is
            // shared with the playlist/EPG/proxy paths that hit the same kind of origins.
            val request = Request.Builder().url(url).header("User-Agent", HttpDefaults.BROWSER_USER_AGENT).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val permanent = IconFailurePolicy.isPermanentFailure(response.code, isNetworkError = false)
                    failureStore.recordFailure(url, isPermanent = permanent)
                    return@withContext null
                }
                val bounded = BoundedByteReader.readBytes(response.body.byteStream(), IconDiskCache.MAX_ICON_BYTES)
                if (bounded !is BoundedBytesResult.Success) {
                    // Permanent: a file over the cap does not get smaller, and this is a fetch that
                    // downloaded MAX_ICON_BYTES before giving up. Left unrecorded it downloaded them
                    // again on every prefetch pass and every scroll past the row.
                    failureStore.recordFailure(url, isPermanent = true)
                    return@withContext null
                }
                // The gap this closes. An origin that answers 200 with an HTML page instead of a
                // picture - which is how hotlink protection commonly refuses, rather than with the
                // 403 the branch above catches - produced a successful response, a body that is not
                // an image, a null from the cache, and *no record anywhere that it had failed*. The
                // failure store was consulted on every attempt and never learned about the one kind
                // of failure that looks like success, so the same URL was refetched forever.
                //
                // Transient, unlike the size cap: an error page is often the moment rather than the
                // URL, and a week of blacklisting for a host having a bad minute is the wrong price.
                // Checked here rather than inferred from a null out of the cache, because the cache
                // also answers null when it could not *write* - a full disk is not the icon's fault
                // and must not blacklist it.
                if (ImageFormatDetector.detect(bounded.bytes) == null) {
                    failureStore.recordFailure(url, isPermanent = false)
                    return@withContext null
                }
                diskCache.put(url, bounded.bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failureStore.recordFailure(url, isPermanent = false)
            null
        }
    }

    private companion object {
        const val MEMORY_CACHE_SIZE = 256
    }
}

private const val TAG = "IconRepository"
