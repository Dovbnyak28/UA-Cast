package com.uacastplayer.data.epg

import com.uacastplayer.epg.BoundedByteReader
import com.uacastplayer.epg.BoundedBytesResult
import com.uacastplayer.playlist.HttpRetryPolicy
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class EpgDownloadResult {
    data class Success(val gzipBytes: ByteArray) : EpgDownloadResult()
    data object SizeLimitExceeded : EpgDownloadResult()
    data class HttpError(val code: Int) : EpgDownloadResult()
    data class ReadError(val message: String?) : EpgDownloadResult()
}

/** Downloads an XMLTV feed's raw gzip bytes as-is - it is never inflated at download time. */
class EpgDownloader(private val client: OkHttpClient) {

    suspend fun download(url: String): EpgDownloadResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: EpgDownloadResult
        do {
            attempt++
            result = attemptOnce(url)
        } while (isRetryable(result, attempt))
        result
    }

    private fun isRetryable(result: EpgDownloadResult, attempt: Int): Boolean = when (result) {
        is EpgDownloadResult.ReadError -> HttpRetryPolicy.shouldRetry(attempt, isNetworkError = true)
        is EpgDownloadResult.HttpError ->
            HttpRetryPolicy.shouldRetry(attempt, isNetworkError = false, httpStatusCode = result.code)
        else -> false
    }

    private fun attemptOnce(url: String): EpgDownloadResult {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return EpgDownloadResult.HttpError(response.code)
                val body = response.body ?: return EpgDownloadResult.HttpError(response.code)
                when (val bounded = BoundedByteReader.readBytes(body.byteStream(), MAX_EPG_BYTES)) {
                    is BoundedBytesResult.Success -> EpgDownloadResult.Success(bounded.bytes)
                    BoundedBytesResult.SizeLimitExceeded -> EpgDownloadResult.SizeLimitExceeded
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            EpgDownloadResult.ReadError(e.message)
        }
    }

    companion object {
        const val MAX_EPG_BYTES = 96 * 1024 * 1024
    }
}
