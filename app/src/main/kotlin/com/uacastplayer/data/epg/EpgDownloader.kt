package com.uacastplayer.data.epg

import androidx.annotation.VisibleForTesting
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
    /**
     * @param cause the exception's *class name*, never its message. An OkHttp IOException's message
     *   routinely carries the URL it failed on - and for an Xtream feed that URL has the user's
     *   username and password in its query string. This value is shown in the diagnostics report a
     *   user emails, so it is kept leak-proof by construction rather than by sanitizing afterwards.
     *   It loses nothing that matters: UnknownHostException, SocketTimeoutException and
     *   SSLHandshakeException are three different problems with three different answers, and the
     *   class name is what tells them apart.
     */
    data class ReadError(val cause: String?) : EpgDownloadResult()
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
        deleteStaleDownloads()
        var attempt = 0
        var result: EpgDownloadResult
        do {
            attempt++
            delay(HttpRetryPolicy.delayBeforeAttemptMillis(attempt))
            result = attemptOnce(url)
        } while (isRetryable(result, attempt))
        result
    }

    /**
     * Deletes temp files left behind by a previous run.
     *
     * Every path through this class and its caller deletes its own file - but none of that runs when
     * the *process* dies mid-download or mid-parse, and an EPG parse is exactly where this app has
     * been killed before (see the OutOfMemoryError entry in CHANGELOG 0.9.0). Nothing else ever swept
     * them up, and because these live in `filesDir` rather than the cache directory, Android will
     * never reclaim them either: found on a real device as **13 orphaned files totalling ~500MB**, on
     * an app whose entire storage footprint was 523MB. One force-stop during a download is enough to
     * strand another 46MB permanently.
     *
     * Age-gated exactly like [com.uacastplayer.data.icons.IconDiskCache]'s equivalent sweep, so a
     * file another download is still writing into can never be pulled out from under it - the one
     * created moments from now is far newer than the cutoff.
     *
     * Called from `EpgRepository` on startup, not only from tests - the annotation this used to
     * carry said otherwise and lint was right to flag it. Sweeping at startup and not only before
     * each download is deliberate: the common case restores from a snapshot and never downloads at
     * all, which is exactly when the stranded temp files would otherwise accumulate unbounded.
     */
    internal fun deleteStaleDownloads() {
        val cutoff = System.currentTimeMillis() - STALE_DOWNLOAD_AGE_MILLIS
        val stale = tempDir.listFiles { file ->
            file.isFile && file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX) &&
                file.lastModified() < cutoff
        } ?: return
        for (file in stale) {
            runCatching { file.delete() }
        }
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
                val file = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, tempDir)
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
            EpgDownloadResult.ReadError(e.javaClass.simpleName)
        }
    }

    companion object {
        const val MAX_EPG_BYTES = 96 * 1024 * 1024

        @VisibleForTesting
        internal const val TEMP_PREFIX = "epg_download_"

        @VisibleForTesting
        internal const val TEMP_SUFFIX = ".tmp"

        /** Generous next to the seconds-to-a-minute a real feed download takes, so the sweep can
         * never delete a file another download is still writing into. */
        @VisibleForTesting
        internal const val STALE_DOWNLOAD_AGE_MILLIS = 60L * 60L * 1000L
    }
}
