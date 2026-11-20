package com.uacastplayer.data.icons

import android.content.Context
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult

import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.icons.CustomIconSourcePolicy
import com.uacastplayer.icons.IconPackSamplePlan
import com.uacastplayer.icons.IconPackSamplePolicy
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.icons.ImageFormatDetector
import com.uacastplayer.playlist.M3uChannel
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class IconPackSampleStatus { FOUND, MISSING, NETWORK, INVALID_IMAGE, TOO_LARGE }
data class IconPackSample(val title: String, val status: IconPackSampleStatus, val bytes: ByteArray? = null)
data class IconPackCheckResult(val plan: IconPackSamplePlan, val samples: List<IconPackSample>)

/** Explicit user-triggered check. No cache writes, source edits, URL logs or background retries. */
class IconPackChecker(
    context: Context,
    client: OkHttpClient = AppHttp.client(connectTimeoutSeconds = 5, readTimeoutSeconds = 5),
) {
    private val appContext = context.applicationContext
    private val client = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    suspend fun check(source: String, channels: List<M3uChannel>): IconPackCheckResult {
        val safeSource = CustomIconSourcePolicy.canonicalize(source)
        val plan = withContext(AppDispatchers.cpu) {
            val owner = currentCoroutineContext()
            IconPackSamplePolicy.select(channels, owner::ensureActive)
        }
        val slots = Semaphore(MAX_CONCURRENT)
        if (safeSource == null) return IconPackCheckResult(
            plan, plan.channels.map { IconPackSample(it.displayName, IconPackSampleStatus.NETWORK) },
        )
        val samples = coroutineScope {
            plan.channels.map { channel -> async { slots.withPermit { fetch(safeSource, channel) } } }.awaitAll()
        }
        return IconPackCheckResult(plan, samples)
    }

    private suspend fun fetch(source: String, channel: M3uChannel): IconPackSample = withContext(AppDispatchers.io) {
        val request = Request.Builder().url(IconResolver.iconUrl(source, requireNotNull(channel.tvgId)))
            .header("User-Agent", HttpDefaults.BROWSER_USER_AGENT).build()
        val sample = try {
            client.newCall(request).executeCancellable { response ->
                when {
                    response.code == HTTP_NOT_FOUND -> IconPackSample(channel.displayName, IconPackSampleStatus.MISSING)
                    !response.isSuccessful -> IconPackSample(channel.displayName, IconPackSampleStatus.NETWORK)
                    else -> decodeSample(channel.displayName, response.body.byteStream())
                }
            }
        } catch (_: IOException) { IconPackSample(channel.displayName, IconPackSampleStatus.NETWORK) }
        currentCoroutineContext().ensureActive()
        if (sample.bytes == null) sample
        else {
            val decoded = appContext.imageLoader.execute(
                ImageRequest.Builder(appContext).data(sample.bytes).size(PREVIEW_SIZE)
                    .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
            )
            if (decoded is SuccessResult) sample
            else sample.copy(status = IconPackSampleStatus.INVALID_IMAGE, bytes = null)
        }
    }

    private fun decodeSample(title: String, input: java.io.InputStream): IconPackSample =
        when (val read = BoundedByteReader.readBytes(input, MAX_SAMPLE_BYTES)) {
            BoundedBytesResult.SizeLimitExceeded -> IconPackSample(title, IconPackSampleStatus.TOO_LARGE)
            is BoundedBytesResult.Success -> if (ImageFormatDetector.detect(read.bytes) == null) {
                IconPackSample(title, IconPackSampleStatus.INVALID_IMAGE)
            } else IconPackSample(title, IconPackSampleStatus.FOUND, read.bytes)
        }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val MAX_CONCURRENT = 2
        const val MAX_SAMPLE_BYTES = 256 * 1024
        const val PREVIEW_SIZE = 96
    }
}
