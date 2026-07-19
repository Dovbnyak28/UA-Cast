package com.uacastplayer.data.icons

import android.content.Context
import androidx.collection.LruCache
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.icons.IconCandidate
import com.uacastplayer.icons.IconFailurePolicy
import com.uacastplayer.icons.IconMemoryCacheKey
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
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

    fun customIconSources(): List<String> = customSourceStore.getBaseUrls()

    fun addCustomIconSource(baseUrl: String) {
        val current = customSourceStore.getBaseUrls()
        if (baseUrl !in current) customSourceStore.saveBaseUrls(current + baseUrl)
    }

    fun removeCustomIconSource(baseUrl: String) {
        customSourceStore.saveBaseUrls(customSourceStore.getBaseUrls() - baseUrl)
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

    private suspend fun resolveIconFileUncached(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): File? {
        val candidates = IconResolver.candidates(
            tvgLogo, epgIconUrl, tvgId,
            customBaseUrls = customSourceStore.getBaseUrls(),
            cdnFallbackUrl = ::cdnFallbackUrl,
        )
        for (candidate in candidates) {
            diskCache.get(candidate.url)?.let { return it }
            if (candidate is IconCandidate.CacheOnly) continue
            if (failureStore.shouldSkip(candidate.url)) continue

            val fetched = fetchAndValidate(candidate.url)
            if (fetched != null) return fetched
        }
        return null
    }

    /**
     * Cache-only lookup for group icon collages (see GroupIconCollage/GroupCollagePolicy): checks
     * [diskCache] for the same candidate chain [resolveIconFile] would try, but never fetches over
     * the network. A group overview can render dozens of cards at once - speculatively fetching
     * icons nobody explicitly asked for yet would multiply traffic for something that's decorative,
     * not the channel list itself.
     */
    suspend fun cachedIconFile(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): File? {
        val candidates = IconResolver.candidates(
            tvgLogo, epgIconUrl, tvgId,
            customBaseUrls = customSourceStore.getBaseUrls(),
            cdnFallbackUrl = ::cdnFallbackUrl,
        )
        for (candidate in candidates) {
            diskCache.get(candidate.url)?.let { return it }
        }
        return null
    }

    suspend fun trimCache() = diskCache.trim()

    private fun cdnFallbackUrl(tvgId: String): String =
        IconResolver.iconUrl(IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL, tvgId)

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
                val body = response.body ?: return@withContext null
                val bounded = BoundedByteReader.readBytes(body.byteStream(), IconDiskCache.MAX_ICON_BYTES)
                if (bounded !is BoundedBytesResult.Success) return@withContext null
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
