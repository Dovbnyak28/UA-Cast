package com.uacastplayer.data.icons

import android.content.Context
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.icons.IconCandidate
import com.uacastplayer.icons.IconFailurePolicy
import com.uacastplayer.icons.IconResolver
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves a channel's icon through the tvg-logo -> EPG-icon -> CDN-by-tvg-id priority chain,
 * consulting/populating [IconDiskCache] and [IconFailureStore] along the way. The CDN fallback is
 * cache-only by design (see [IconCandidate.CacheOnly]) - it is never speculatively fetched.
 */
class IconRepository(context: Context) {

    private val appContext = context.applicationContext
    private val diskCache = IconDiskCache(appContext)
    private val failureStore = IconFailureStore(appContext)
    private val customSourceStore = CustomIconSourceStore(appContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun customIconSources(): List<String> = customSourceStore.getBaseUrls()

    fun addCustomIconSource(baseUrl: String) {
        val current = customSourceStore.getBaseUrls()
        if (baseUrl !in current) customSourceStore.saveBaseUrls(current + baseUrl)
    }

    fun removeCustomIconSource(baseUrl: String) {
        customSourceStore.saveBaseUrls(customSourceStore.getBaseUrls() - baseUrl)
    }

    suspend fun resolveIconFile(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): File? {
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

    suspend fun trimCache() = diskCache.trim()

    private fun cdnFallbackUrl(tvgId: String): String =
        IconResolver.iconUrl(IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL, tvgId)

    private suspend fun fetchAndValidate(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
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
}
