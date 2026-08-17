package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.PlaylistLoadResult
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a failed playlist download is allowed to say about itself.
 *
 * [PlaylistLoadResult.ReadError.message] is meant to carry only the failing exception's class
 * name, never `e.message` - see that field's own doc. `IOException.message` on a real network
 * failure routinely names the request URL, and this app's own playlist URLs are commonly an
 * Xtream address with a username and password sitting in the query string. Nothing downstream
 * shows this field today, but that is not the same as it being safe to put there.
 *
 * The failure here is real, not simulated: a `ServerSocket` opened and immediately closed, so the
 * port is guaranteed to have nobody listening on it. `OkHttp`'s `ConnectException` for that case
 * genuinely does put the host and port in its message, which is exactly the shape of leak this
 * class exists to prevent - this is not a hypothetical about what `e.message` *could* contain.
 */
class PlaylistUrlLoaderTest {

    /** A port nothing is listening on, freed the instant it's handed out. */
    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `a connection failure never carries the url into the result`() = runTest {
        val port = deadPort()
        val host = "127.0.0.1"
        val url = "http://$host:$port/playlist.m3u?username=realuser&password=realsecret"
        val loader = PlaylistUrlLoader(OkHttpClient())

        val result = loader.load(url)

        assertTrue("expected a ReadError for a port nothing listens on", result is PlaylistLoadResult.ReadError)
        val message = (result as PlaylistLoadResult.ReadError).message

        // The property under test, stated both ways: the class name is exactly what a caller gets,
        // and the credential-bearing pieces of the url are nowhere in it.
        assertEquals("ConnectException", message)
        assertFalse("the host leaked into the result", message.orEmpty().contains(host))
        assertFalse("the password leaked into the result", message.orEmpty().contains("realsecret"))
    }
}
