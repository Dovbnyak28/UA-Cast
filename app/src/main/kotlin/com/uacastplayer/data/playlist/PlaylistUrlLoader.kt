package com.uacastplayer.data.playlist

import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.playlist.CharsetDetector
import com.uacastplayer.playlist.HttpRetryPolicy
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a playlist over HTTP(S), capping the body size and retrying only transient errors. */
class PlaylistUrlLoader(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {

    suspend fun load(url: String): PlaylistLoadResult = withContext(ioDispatcher) {
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

    private suspend fun attemptOnce(url: String): PlaylistLoadResult {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", HttpDefaults.BROWSER_USER_AGENT).build()
            client.newCall(request).executeCancellable { response ->
                if (!response.isSuccessful) {
                    PlaylistLoadResult.HttpError(response.code)
                } else {
                    val body = response.body
                    // The Content-Type charset is passed to CharsetDetector as a hint, NOT used directly.
                    // It used to be treated as authoritative, and IPTV panels are wrong about it often
                    // enough to matter: a body that is really UTF-8 served as `charset=windows-1251`
                    // turned every Cyrillic channel name into mojibake. See CharsetDetector.detect's
                    // second overload for the precedence and why the bytes get to overrule the server.
                    val declaredCharset = body.contentType()?.charset()
                    when (val bounded = BoundedByteReader.readBytes(body.byteStream(), MAX_PLAYLIST_BYTES)) {
                        is BoundedBytesResult.Success -> {
                            val charset = CharsetDetector.detect(bounded.bytes, declaredCharset)
                            PlaylistLoadResult.Success(String(bounded.bytes, charset))
                        }
                        BoundedBytesResult.SizeLimitExceeded -> PlaylistLoadResult.SizeLimitExceeded
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // A saved source can also arrive from a hand-edited/third-party backup, bypassing the
            // add-screen validator. OkHttp reports malformed and non-HTTP URLs while building the
            // request, before any IOException exists; keep that untrusted input inside the same
            // result boundary instead of letting it escape through viewModelScope.
            PlaylistLoadResult.ReadError(e.javaClass.simpleName)
        } catch (e: IOException) {
            // e.javaClass.simpleName, not e.message - see PlaylistLoadResult.ReadError's own doc.
            // An IOException from this specific call routinely names the request URL, and that URL
            // is this app's playlist address - for an Xtream source, with its username/password as
            // query parameters right there in the string.
            PlaylistLoadResult.ReadError(e.javaClass.simpleName)
        }
    }

    companion object {
        const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024
    }
}
