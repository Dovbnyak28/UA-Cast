package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.BoundedReadResult
import com.uacastplayer.playlist.BoundedTextReader
import com.uacastplayer.playlist.HttpRetryPolicy
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
            val request = Request.Builder().url(url).header("User-Agent", BROWSER_USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return PlaylistLoadResult.HttpError(response.code)
                val body = response.body ?: return PlaylistLoadResult.HttpError(response.code)
                when (val bounded = BoundedTextReader.readText(body.byteStream(), MAX_PLAYLIST_BYTES)) {
                    is BoundedReadResult.Success -> PlaylistLoadResult.Success(bounded.text)
                    BoundedReadResult.SizeLimitExceeded -> PlaylistLoadResult.SizeLimitExceeded
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
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36"
    }
}
