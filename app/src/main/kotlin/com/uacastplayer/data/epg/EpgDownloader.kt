package com.uacastplayer.data.epg

import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedFileCopyResult
import com.uacastplayer.playlist.HttpRetryPolicy
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class EpgDownloadResult {
    data class Success(val documentFile: File) : EpgDownloadResult()
    data object SizeLimitExceeded : EpgDownloadResult()
    data class HttpError(val code: Int) : EpgDownloadResult()
    data class ReadError(val message: String?) : EpgDownloadResult()
}

/**
 * Downloads an XMLTV feed's raw bytes as-is - never inflated at download time, since some feeds
 * are gzip-compressed and others are already-plain XML (see [EpgSource]). Streams straight to a
 * temp file under [tempDir] instead of buffering in memory: feeds can run tens of megabytes, and
 * a single in-memory ByteArray that size is wasteful on top of whatever else is loaded at once.
 * Callers own the returned [EpgDownloadResult.Success.documentFile] and must delete it once done.
 */
class EpgDownloader(private val client: OkHttpClient, private val tempDir: File) {

    suspend fun download(url: String): EpgDownloadResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: EpgDownloadResult
        do {
            attempt++
            delay(HttpRetryPolicy.delayBeforeAttemptMillis(attempt))
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
        // Tracked separately from the temp file created inside the response block below, so the
        // catch clauses can clean up a partially-written file regardless of where the failure hit.
        var tempFile: File? = null
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return EpgDownloadResult.HttpError(response.code)
                val body = response.body ?: return EpgDownloadResult.HttpError(response.code)
                val file = File.createTempFile("epg_download_", ".tmp", tempDir)
                tempFile = file
                when (BoundedByteReader.copyToFile(body.byteStream(), file, MAX_EPG_BYTES)) {
                    is BoundedFileCopyResult.Success -> EpgDownloadResult.Success(file)
                    BoundedFileCopyResult.SizeLimitExceeded -> {
                        file.delete()
                        EpgDownloadResult.SizeLimitExceeded
                    }
                }
            }
        } catch (e: CancellationException) {
            tempFile?.delete()
            throw e
        } catch (e: IOException) {
            tempFile?.delete()
            EpgDownloadResult.ReadError(e.message)
        }
    }

    companion object {
        const val MAX_EPG_BYTES = 96 * 1024 * 1024
    }
}
