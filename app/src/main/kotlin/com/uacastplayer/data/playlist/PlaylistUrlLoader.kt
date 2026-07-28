package com.uacastplayer.data.playlist

import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.playlist.BoundedBytesResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.CharsetDetector
import com.uacastplayer.playlist.HttpRetryPolicy
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a playlist over HTTP(S), capping the body size and retrying only transient errors. */
class PlaylistUrlLoader(private val client: OkHttpClient) {

    suspend fun load(url: String): PlaylistLoadResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: PlaylistLoadResult
        do {
            attempt++
            delay(HttpRetryPolicy.delayBeforeAttemptMillis(attempt))
            result = attemptOnce(url)
        } while (isRetryable(result, attempt))
        result
    }

    private fun isRetryable(result: PlaylistLoadResult, attempt: Int): Boolean = when (result) {
        is PlaylistLoadResult.ReadError -> HttpRetryPolicy.shouldRetry(attempt, isNetworkError = true)
        is PlaylistLoadResult.HttpError ->
            HttpRetryPolicy.shouldRetry(attempt, isNetworkError = false, httpStatusCode = result.code)
        else -> false
    }

    private fun attemptOnce(url: String): PlaylistLoadResult {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", HttpDefaults.BROWSER_USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return PlaylistLoadResult.HttpError(response.code)
                val body = response.body ?: return PlaylistLoadResult.HttpError(response.code)
                // An explicit charset in the response's own Content-Type is authoritative - only
                // fall back to sniffing the bytes (see CharsetDetector) when the server didn't say.
                val declaredCharset = body.contentType()?.charset()
                when (val bounded = BoundedTextReader.readBytes(body.byteStream(), MAX_PLAYLIST_BYTES)) {
                    is BoundedBytesResult.Success -> {
                        val charset = declaredCharset ?: CharsetDetector.detect(bounded.bytes)
                        PlaylistLoadResult.Success(String(bounded.bytes, charset))
                    }
                    BoundedBytesResult.SizeLimitExceeded -> PlaylistLoadResult.SizeLimitExceeded
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PlaylistLoadResult.ReadError(e.message)
        }
    }

    companion object {
        const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024
    }
}
