package com.uacastplayer.data.cast

import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerTest {

    private val server = ProxyServer(OkHttpClient())

    @After
    fun tearDown() {
        server.stop()
    }

    private fun fakeResponse(url: String, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()

    @Test
    fun `sub-resources discovered while rewriting a playlist inherit its user-agent and referrer`() {
        server.start(sessionToken = "test-session", host = "127.0.0.1")
        val parent = ResourceEntry(
            type = RESOURCE_TYPE_PLAYLIST,
            originalUrl = "https://origin.example/playlist.m3u8",
            userAgent = "CustomAgent/1.0",
            referrer = "https://example.com/",
        )
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Channel
            segment1.ts
        """.trimIndent()
        val output = ByteArrayOutputStream()

        server.servePlaylist(fakeResponse("https://origin.example/playlist.m3u8", playlist), "GET", output, parent)

        val mediaEntry = server.resourcesForTesting().values.single { it.type == RESOURCE_TYPE_MEDIA }
        assertEquals("CustomAgent/1.0", mediaEntry.userAgent)
        assertEquals("https://example.com/", mediaEntry.referrer)
        assertEquals("https://origin.example/segment1.ts", mediaEntry.originalUrl)
    }

    @Test
    fun `a sub-resource with no referrer inherits none`() {
        server.start(sessionToken = "test-session", host = "127.0.0.1")
        val parent = ResourceEntry(
            type = RESOURCE_TYPE_PLAYLIST,
            originalUrl = "https://origin.example/playlist.m3u8",
            userAgent = "CustomAgent/1.0",
            referrer = null,
        )
        val playlist = "#EXTM3U\n#EXTINF:-1,Channel\nsegment1.ts"
        val output = ByteArrayOutputStream()

        server.servePlaylist(fakeResponse("https://origin.example/playlist.m3u8", playlist), "GET", output, parent)

        val mediaEntry = server.resourcesForTesting().values.single { it.type == RESOURCE_TYPE_MEDIA }
        assertEquals("CustomAgent/1.0", mediaEntry.userAgent)
        assertEquals(null, mediaEntry.referrer)
    }

    @Test(expected = IllegalStateException::class)
    fun `buildLocalUrl fails fast when the server is not running`() {
        server.registerPlaylist("https://origin.example/playlist.m3u8")
            .let(server::buildLocalUrl)
    }

    @Test
    fun `ensureStarted with the same token and host is a no-op that keeps existing resources`() {
        val firstPort = server.ensureStarted(sessionToken = "session-a", host = "127.0.0.1")
        server.registerPlaylist("https://origin.example/one.m3u8")
        val secondPort = server.ensureStarted(sessionToken = "session-a", host = "127.0.0.1")
        assertEquals(firstPort, secondPort)
        assertEquals(1, server.resourcesForTesting().size)
    }

    @Test
    fun `ensureStarted with a different token restarts and clears previous resources`() {
        server.ensureStarted(sessionToken = "session-a", host = "127.0.0.1")
        server.registerPlaylist("https://origin.example/one.m3u8")
        server.ensureStarted(sessionToken = "session-b", host = "127.0.0.1")
        assertEquals(0, server.resourcesForTesting().size)
    }

    @Test
    fun `ensureStarted with a different host restarts and clears previous resources`() {
        server.ensureStarted(sessionToken = "session-a", host = "127.0.0.1")
        server.registerPlaylist("https://origin.example/one.m3u8")
        server.ensureStarted(sessionToken = "session-a", host = "192.168.1.5")
        assertEquals(0, server.resourcesForTesting().size)
    }

    @Test
    fun `ensureStarted after the server was never started behaves like start`() {
        server.ensureStarted(sessionToken = "session-a", host = "127.0.0.1")
        val resourceId = server.registerPlaylist("https://origin.example/one.m3u8")
        assertTrue(server.buildLocalUrl(resourceId).startsWith("http://127.0.0.1:"))
    }
}
