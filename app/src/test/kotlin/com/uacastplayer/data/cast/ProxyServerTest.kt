package com.uacastplayer.data.cast

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
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

    @Test
    fun `every response carries CORS headers for the receiver's web app`() {
        server.start(sessionToken = "test-session", host = "127.0.0.1")
        val parent = ResourceEntry(
            type = RESOURCE_TYPE_PLAYLIST,
            originalUrl = "https://origin.example/playlist.m3u8",
            userAgent = "CustomAgent/1.0",
            referrer = null,
        )
        val output = ByteArrayOutputStream()

        val upstream = fakeResponse("https://origin.example/playlist.m3u8", "#EXTM3U\nseg.ts")
        server.servePlaylist(upstream, "GET", output, parent)

        // The Default Media Receiver fetches everything via cross-origin XHR - a response without
        // this header is silently discarded by the receiver's browser, killing playback.
        val response = output.toString(Charsets.ISO_8859_1.name())
        assertTrue(response.contains("Access-Control-Allow-Origin: *"))
        assertTrue(response.contains("Access-Control-Expose-Headers: Content-Length, Content-Range"))
    }

    @Test(expected = IllegalStateException::class)
    fun `buildLocalUrl fails fast when the server is not running`() {
        server.registerPlaylist("https://origin.example/playlist.m3u8")
            .let(server::buildLocalUrl)
    }

    @Test
    fun `a malformed provider stream URL returns 502 instead of dropping the receiver socket`() {
        val port = server.start(sessionToken = "test-session", host = "127.0.0.1")
        val resourceId = server.registerPlaylist("not a valid url")
        val target = URI(server.buildLocalUrl(resourceId))

        val response = Socket("127.0.0.1", port).use { receiver ->
            receiver.soTimeout = 3_000
            receiver.getOutputStream().apply {
                write(
                    ("GET ${target.rawPath} HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            receiver.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }

        assertTrue(response.startsWith("HTTP/1.1 502 Bad Gateway"))
        assertTrue(response.contains("Content-Length: 0"))
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

    @Test
    fun `stop cancels an upstream call still waiting for response headers`() {
        val upstreamStarted = CountDownLatch(1)
        val upstreamCancelled = CountDownLatch(1)
        val blockingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                upstreamStarted.countDown()
                while (!chain.call().isCanceled()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10))
                }
                upstreamCancelled.countDown()
                throw IOException("cancelled upstream")
            }
            .build()
        val stoppableServer = ProxyServer(blockingClient)
        val port = stoppableServer.start(sessionToken = "test-session", host = "127.0.0.1")
        val resourceId = stoppableServer.registerPlaylist("https://origin.example/live.m3u8")
        val target = URI(stoppableServer.buildLocalUrl(resourceId))

        try {
            Socket("127.0.0.1", port).use { receiver ->
                receiver.getOutputStream().apply {
                    write(
                        ("GET ${target.rawPath} HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\nConnection: close\r\n\r\n").toByteArray(),
                    )
                    flush()
                }
                assertTrue("proxy never opened the upstream Call", upstreamStarted.await(3, TimeUnit.SECONDS))

                stoppableServer.stop()

                assertTrue(
                    "proxy stop left its upstream Call active",
                    upstreamCancelled.await(1, TimeUnit.SECONDS),
                )
            }
        } finally {
            stoppableServer.stop()
        }
    }

    private fun servePlaylistOnce(method: String, output: ByteArrayOutputStream) {
        val parent = ResourceEntry(
            type = RESOURCE_TYPE_PLAYLIST,
            originalUrl = "https://origin.example/playlist.m3u8",
            userAgent = "Agent/1.0",
            referrer = null,
        )
        val playlist = "#EXTM3U\n#EXTINF:-1,Channel\nsegment1.ts"
        server.servePlaylist(fakeResponse("https://origin.example/playlist.m3u8", playlist), method, output, parent)
    }

    @Test
    fun `serving a body to the receiver advances the delivered-bytes counter`() {
        server.start(sessionToken = "test-session", host = "127.0.0.1")
        assertEquals(0L, server.bytesServedToReceiver())

        val output = ByteArrayOutputStream()
        servePlaylistOnce("GET", output)

        assertEquals(output.size().toLong(), server.bytesServedToReceiver() + headerBytesIn(output))
        assertTrue(server.bytesServedToReceiver() > 0L)
    }

    /** Only bytes the receiver actually receives count - a HEAD gets the headers and no body, so
     * it is not evidence that media is moving. */
    @Test
    fun `a HEAD request delivers no body and so advances nothing`() {
        server.start(sessionToken = "test-session", host = "127.0.0.1")
        servePlaylistOnce("HEAD", ByteArrayOutputStream())
        assertEquals(0L, server.bytesServedToReceiver())
    }

    /** The stall watchdog reads deltas, so a restart that zeroed this would read as "no progress"
     * and fire the very watchdog the counter exists to keep quiet - see ProxyServer's own note. */
    @Test
    fun `restarting the server does not reset the delivered-bytes counter`() {
        server.start(sessionToken = "session-a", host = "127.0.0.1")
        servePlaylistOnce("GET", ByteArrayOutputStream())
        val before = server.bytesServedToReceiver()

        server.start(sessionToken = "session-b", host = "127.0.0.1")

        assertEquals(before, server.bytesServedToReceiver())
    }

    /** The counter tracks bodies, not whole responses; the headers written alongside are not part
     * of it, so a test comparing against the raw stream has to subtract them. */
    private fun headerBytesIn(output: ByteArrayOutputStream): Long {
        val text = output.toString(Charsets.ISO_8859_1.name())
        return (text.indexOf("\r\n\r\n") + "\r\n\r\n".length).toLong()
    }
}
