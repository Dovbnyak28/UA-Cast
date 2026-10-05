package com.uacastplayer.data.icons

import android.content.Context
import androidx.collection.LruCache
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.icons.CastArtworkPolicy
import com.uacastplayer.icons.IconCandidate
import com.uacastplayer.icons.IconFailurePolicy
import com.uacastplayer.icons.IconMemoryCacheKey
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.icons.CustomIconSourcePolicy
import com.uacastplayer.icons.ImageFormatDetector
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.log.AppLog
import com.uacastplayer.log.RepeatedNoteFilter
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Wraps a resolved icon [File] so a *negative* result (channel has no icon) can be cached in
 * [IconRepository.memoryCache] too - [LruCache.get] returning null is otherwise indistinguishable
 * from a cache miss. */
private data class CachedIcon(val file: File?)

/**
 * Resolves channel logos only through packs explicitly added by the user, consulting/populating
 * [IconDiskCache] and [IconFailureStore]. Playlist/EPG metadata and old automatic cache entries
 * are not logo sources.
 *
 * [memoryCache] sits in front of all of that: [IconDiskCache] still hits actual disk I/O on every
 * call, and a channel with no resolvable icon would otherwise repeat the full candidate chain
 * (including disk lookups) on every scroll-triggered recomposition. It's invalidated wholesale by
 * [invalidateMemoryCache] whenever the on-disk picture can have changed underneath it (icon cache
 * cleared, prefetch finished writing new files) rather than tracked per-entry.
 */
class IconRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
    private val trimAfterWrites: Int = TRIM_AFTER_WRITES,
    private val trimAfterBytes: Long = TRIM_AFTER_BYTES,
) {

    private val appContext = context.applicationContext
    private val diskCache = IconDiskCache(appContext, ioDispatcher)
    private val failureStore = IconFailureStore(appContext)
    private val customSourceStore = CustomIconSourceStore(appContext)
    private val memoryCache = LruCache<String, CachedIcon>(MEMORY_CACHE_SIZE)
    private val cacheLock = Any()
    private var cacheGeneration = 0L
    private var writesSinceTrim = 0
    private var bytesSinceTrim = 0L
    private val trimMutex = Mutex()
    private val resolutionCoordinator = IconResolutionCoordinator()
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

    private fun customBaseUrls(): List<String> = synchronized(cacheLock) {
        cachedCustomBaseUrls ?: customSourceStore.getBaseUrls().also { cachedCustomBaseUrls = it }
    }

    fun customIconSources(): List<String> = customBaseUrls()

    fun addCustomIconSource(baseUrl: String) = synchronized(cacheLock) {
        val safeUrl = CustomIconSourcePolicy.canonicalize(baseUrl) ?: return@synchronized
        val current = customBaseUrls()
        if (safeUrl !in current) {
            customSourceStore.saveBaseUrls(current + safeUrl)
            cachedCustomBaseUrls = null
            invalidateMemoryCache()
        }
    }

    fun removeCustomIconSource(baseUrl: String) = synchronized(cacheLock) {
        customSourceStore.saveBaseUrls(customBaseUrls() - baseUrl)
        cachedCustomBaseUrls = null
        invalidateMemoryCache()
    }

    /** Drops every entry, positive and negative - see the class doc for when this needs calling. */
    fun invalidateMemoryCache() = synchronized(cacheLock) {
        cacheGeneration++
        memoryCache.evictAll()
    }

    /**
     * Re-opens URLs that failed transiently and drops negative memory results so the next resolve
     * can actually reach the network. Called on playlist refresh and unmetered-network recovery;
     * permanent 4xx/5xx records remain protected by [IconFailureStore].
     */
    fun retryTransientFailures() = synchronized(cacheLock) {
        // Retire publishers atomically with clearing their records, not in two raceable steps.
        failureStore.clearTransientFailures()
        invalidateMemoryCache()
    }

    suspend fun resolveIconFile(tvgId: String?): File? {
        // With no matching ID/pack there is no disk or network work to schedule per visible row.
        if (tvgId.isNullOrBlank()) return null
        val cacheKey = IconMemoryCacheKey.of(tvgId)
        val sources: List<String>
        val cached: CachedIcon?
        val generation = synchronized(cacheLock) {
            sources = customBaseUrls()
            cached = memoryCache.get(cacheKey)
            cacheGeneration
        }
        return when {
            sources.isEmpty() -> null
            cached != null -> cached.file
            else -> resolveAndPublishIcon(tvgId, cacheKey, sources, generation)
        }
    }

    private suspend fun resolveAndPublishIcon(
        tvgId: String,
        cacheKey: String,
        sources: List<String>,
        generation: Long,
    ): File? = resolutionCoordinator.withResolution(cacheKey, generation) {
        // Another row/prefetch may have completed while this caller waited. A cached miss is a
        // result too; do not repeat its candidate chain. New revisions never wait for old IO.
        val cached = synchronized(cacheLock) {
            if (generation == cacheGeneration) memoryCache.get(cacheKey) else null
        }
        if (cached != null) cached.file else {
            val resolved = resolveIconFileUncached(tvgId, sources, generation)
            publishResolvedIcon(cacheKey, sources, generation, resolved)
        }
    }

    private fun publishResolvedIcon(cacheKey: String, sources: List<String>, generation: Long,
        resolved: File?): File? = synchronized(cacheLock) {
        // A removed pack must not publish its late network result to a still-visible row.
        if (sources != customBaseUrls()) return@synchronized null
        // Invalidation retires in-flight publishers too. Within one generation a failed
        // duplicate must not replace an icon another resolver has already downloaded.
        if (generation == cacheGeneration && (resolved != null || memoryCache.get(cacheKey)?.file == null)) {
            memoryCache.put(cacheKey, CachedIcon(resolved))
        }
        resolved
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
        tvgId: String?,
        sources: List<String>,
        generation: Long,
    ): File? = withContext(ioDispatcher) {
        val candidates = IconResolver.candidates(tvgId, sources)
        for (candidate in candidates) {
            if (sources != customBaseUrls()) return@withContext null
            resolveCandidate(candidate, generation)?.let { return@withContext it }
        }
        null
    }

    private suspend fun resolveCandidate(candidate: IconCandidate, generation: Long): File? {
        val cached = diskCache.get(candidate.url)
        return cached ?: if (
            failureStore.shouldSkip(candidate.url)
        ) {
            null
        } else {
            fetchAndValidate(candidate.url, generation)
        }
    }

    /**
     * The URL a Cast receiver should be given as artwork for this channel, picked out of the same
     * candidate chain [resolveIconFile] walks. [CastArtworkPolicy] selects the first user pack;
     * the receiver fetches that URL itself, so availability is not verified here.
     *
     * Unlike its two neighbours this touches neither disk nor network: it builds the chain and
     * picks a URL out of it, so it is safe to call straight from the main thread while switching
     * channels. The receiver does its own fetching.
     */
    fun castArtworkUrl(tvgId: String?): String? {
        val candidates = IconResolver.candidates(tvgId, customBaseUrls())
        val url = CastArtworkPolicy.artworkUrl(candidates)
        // `cast load: artwork=false` in CastSessionRepository says a receiver got no picture; it
        // cannot say why: no selected pack or no matching channel ID both yield no artwork.
        // Never log the URL itself: this ends up in a shared diagnostics report.
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

    suspend fun trimCache() = trimMutex.withLock {
        synchronized(cacheLock) {
            writesSinceTrim = 0
            bytesSinceTrim = 0L
        }
        diskCache.trim()
        // Trimming can evict a file that a positive memory entry still points at. Returning that
        // stale File would make Coil fail from a path that no longer exists.
        invalidateMemoryCache()
    }

    /** Lazy row loads also write icons, even on low-end devices where prefetch never runs. */
    private fun shouldTrimAfterWrite(bytes: Int): Boolean = synchronized(cacheLock) {
        writesSinceTrim++
        bytesSinceTrim += bytes
        if (writesSinceTrim >= trimAfterWrites || bytesSinceTrim >= trimAfterBytes) {
            writesSinceTrim = 0
            bytesSinceTrim = 0L
            true
        } else {
            false
        }
    }

    /** See [IconFailureStore.pruneExpiredFailures] - call once per playlist load/refresh. */
    fun pruneExpiredFailures() = failureStore.pruneExpiredFailures()

    // The IOException itself is never surfaced beyond marking this URL as a transient failure
    // (see IconFailurePolicy) - there's nothing about a network/read error worth logging per icon.
    @Suppress("SwallowedException")
    private suspend fun fetchAndValidate(url: String, generation: Long): File? = withContext(ioDispatcher) {
        try {
            // Many icon hosts have hotlink protection that 403s the default OkHttp UA (which
            // identifies itself as "okhttp/<version>") - a browser-looking UA gets through the
            // same check a real browser would pass. See HttpDefaults for why this constant is
            // shared with the playlist/EPG/proxy paths that hit the same kind of origins.
            val request = Request.Builder().url(url).header("User-Agent", HttpDefaults.BROWSER_USER_AGENT).build()
            val fetched = httpClient.newCall(request).executeCancellable { response ->
                if (!response.isSuccessful) {
                    IconFetchResult.HttpError(response.code)
                } else {
                    when (val bounded = BoundedByteReader.readBytes(
                        response.body.byteStream(),
                        IconDiskCache.MAX_ICON_BYTES,
                    )) {
                        is BoundedBytesResult.Success -> {
                            if (ImageFormatDetector.detect(bounded.bytes) == null) {
                                IconFetchResult.InvalidImage
                            } else {
                                IconFetchResult.Ready(bounded.bytes)
                            }
                        }
                        BoundedBytesResult.SizeLimitExceeded -> IconFetchResult.TooLarge
                    }
                }
            }
            when (fetched) {
                is IconFetchResult.HttpError -> {
                    val permanent = IconFailurePolicy.isPermanentFailure(fetched.code, isNetworkError = false)
                    recordFailureIfCurrent(url, isPermanent = permanent, generation)
                    null
                }
                IconFetchResult.TooLarge -> {
                    // Permanent: a file over the cap does not get smaller, and this fetch already
                    // downloaded MAX_ICON_BYTES. Without a record every prefetch/scroll repeats it.
                    recordFailureIfCurrent(url, isPermanent = true, generation)
                    null
                }
                IconFetchResult.InvalidImage -> {
                    // The gap this closes. An origin that answers 200 with an HTML page instead of a
                    // picture - which is how hotlink protection commonly refuses, rather than with
                    // the 403 branch above - produced a successful response and a body that is not
                    // an image, but no record anywhere that it failed. The failure store was
                    // consulted on every attempt and never learned about that kind of failure, so
                    // the same URL was refetched forever.
                    //
                    // Transient, unlike the size cap: an error page is often the moment rather than
                    // the URL, and a week of blacklisting for a bad minute is the wrong price. This
                    // is checked here rather than inferred from a null out of the cache, because the
                    // cache also answers null when it could not write; a full disk is not the icon's
                    // fault and must not blacklist it.
                    recordFailureIfCurrent(url, isPermanent = false, generation)
                    null
                }
                is IconFetchResult.Ready -> {
                    val file = diskCache.put(url, fetched.bytes)
                    if (file != null && shouldTrimAfterWrite(fetched.bytes.size)) {
                        // Maintenance must never turn a successfully downloaded icon into a miss.
                        // runCatchingNonFatal keeps cancellation and fatal VM errors intact.
                        runCatchingNonFatal { trimCache() }.onFailure { error ->
                            AppLog.w(TAG) { "Icon cache trim failed: ${error.javaClass.simpleName}" }
                        }
                    }
                    file
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalArgumentException) {
            // User pack URLs and channel IDs are untrusted. OkHttp rejects a malformed URL
            // before a Call exists, so this is neither a network IOException nor something a
            // retry can repair. Remember it as permanent instead of letting one bad logo cancel
            // the whole prefetch (or crash a row's produceState coroutine).
            recordFailureIfCurrent(url, isPermanent = true, generation)
            null
        } catch (e: IOException) {
            recordFailureIfCurrent(url, isPermanent = false, generation)
            null
        }
    }

    private fun recordFailureIfCurrent(url: String, isPermanent: Boolean, generation: Long) =
        synchronized(cacheLock) {
            // An obsolete 404/503 must not blacklist a URL after a fresh retry already succeeded.
            if (generation == cacheGeneration) failureStore.recordFailure(url, isPermanent)
        }

    private companion object {
        const val MEMORY_CACHE_SIZE = 256
        const val TRIM_AFTER_WRITES = 256
        const val TRIM_AFTER_BYTES = 32L * 1024 * 1024
    }
}

private const val TAG = "IconRepository"

private sealed interface IconFetchResult {
    data class HttpError(val code: Int) : IconFetchResult
    data class Ready(val bytes: ByteArray) : IconFetchResult
    data object TooLarge : IconFetchResult
    data object InvalidImage : IconFetchResult
}
