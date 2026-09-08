package com.uacastplayer.cast

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.URI
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastProxySessionTest {
    @Test
    fun `proxy owner preserves same-session resources and closes the listening socket on stop`() {
        val proxy = CastProxySession(ApplicationProvider.getApplicationContext<Application>(), OkHttpClient())
        try {
            proxy.adoptToken("regression-session")
            proxy.beginPlaybackAttempt()
            val first = proxy.prepare("127.0.0.1", "https://example.test/live.m3u8", "Live", "agent", null, "TV")
                .getOrThrow()
            val uri = URI(first.localUrl)
            Socket().use { it.connect(InetSocketAddress(uri.host, uri.port), 1_000) }
            proxy.adoptToken("regression-session")
            val resumed = proxy.prepare("127.0.0.1", "https://example.test/live.m3u8", "Live", "agent", null, "TV")
                .getOrThrow()
            assertEquals(first, resumed)
            val changed = proxy.prepare("127.0.0.1", "https://example.test/live.m3u8", "Live", "new-agent", null, "TV")
                .getOrThrow()
            assertNotEquals(first.resourceId, changed.resourceId)
            proxy.stop()
            proxy.stop()
            assertStoppedProxyRejectsRequest(uri)
        } finally {
            proxy.stop()
        }
    }

    private fun assertStoppedProxyRejectsRequest(uri: URI) {
        // JDK socket close can defer descriptor disposal until the blocked accept() unwinds.
        // A TCP handshake alone is not evidence that the stopped proxy still serves requests.
        // Require refusal/reset or EOF without any response bytes; a read timeout must fail.
        val response = runCatching {
            Socket().use { socket ->
                socket.soTimeout = 1_000
                socket.connect(InetSocketAddress(uri.host, uri.port), 1_000)
                socket.getOutputStream().write(
                    "GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.US_ASCII),
                )
                socket.getInputStream().read()
            }
        }
        response.fold(
            onSuccess = { assertEquals("Stopped proxy must not return response bytes", -1, it) },
            onFailure = { assertTrue("Expected socket refusal/reset, not $it", it is SocketException) },
        )
    }
}
