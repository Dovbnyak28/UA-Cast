package com.uacastplayer.data.cast

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HLS-to-TS flattening path, on a real device.
 *
 * The same ground is covered by `ProxyRendererProfileTest` on the JVM, and that is not a reason to
 * skip it here. Every fault this pins was one of ordering, buffering or socket lifetime - a HEAD
 * whose bytes never left a `BufferedOutputStream`, headers committed before the first byte was
 * known to exist - and those are precisely the things a desktop JVM's sockets and OkHttp can agree
 * to do while an Android device does not. This path exists for exactly one purpose: to be read by a
 * TV over the phone's own Wi-Fi. It should be proven on the phone.
 *
 * Nothing here touches the installed app's data. It binds its own origin on a loopback port, runs
 * its own [ProxyServer], and stops both.
 */
class ProxyFlattenInstrumentedTest {

    private var origin: Origin? = null
    private var proxy: ProxyServer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @After
    fun tearDown() {
        proxy?.stop()
        origin?.close()
    }

    /** A routable origin: any path can be given a status, a content type and a body, and anything
     * unregistered is a 404 rather than an empty 200 - which is what a real origin does, and the
     * difference the "no segments" case turns on. */
    private class Origin : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val routes = mutableMapOf<String, Triple<String, String, ByteArray>>()
        private val redirects = mutableMapOf<String, String>()
        private val hits = mutableMapOf<String, AtomicInteger>()

        fun urlFor(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        fun hitsFor(path: String): Int = synchronized(hits) { hits[path]?.get() ?: 0 }

        fun route(path: String, contentType: String, body: ByteArray) {
            synchronized(routes) { routes[path] = Triple("200 OK", contentType, body) }
        }

        fun redirect(from: String, to: String) {
            synchronized(redirects) { redirects[from] = to }
        }

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val accepted = socket.accept()
                        worker.execute { serve(accepted) }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        private fun serve(client: Socket) {
            client.use {
                val head = ByteArray(HEAD_BUFFER_BYTES)
                val read = it.getInputStream().read(head)
                val line = String(head, 0, maxOf(read, 0)).lineSequence().firstOrNull().orEmpty()
                val parts = line.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty().substringBefore('?')
                synchronized(hits) { hits.getOrPut(path) { AtomicInteger(0) }.incrementAndGet() }
                val out = it.getOutputStream()
                val target = synchronized(redirects) { redirects[path] }
                if (target != null) {
                    out.write("HTTP/1.1 302 Found\r\nLocation: $target\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return@use
                }
                val (status, type, body) = synchronized(routes) { routes[path] }
                    ?: Triple("404 Not Found", "text/plain", ByteArray(0))
                out.write(
                    "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(),
                )
                if (method != "HEAD") out.write(body)
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    /** Well-formed MPEG-TS: 0x47 every 188 bytes, so the proxy's own sniffing sees a real stream. */
    private fun tsBytes(packets: Int = TS_PACKETS): ByteArray =
        ByteArray(packets * TS_PACKET_SIZE) { if (it % TS_PACKET_SIZE == 0) 0x47.toByte() else (it % 251).toByte() }

    private fun mediaPlaylist(segment: String) = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:4")
        appendLine("#EXT-X-MEDIA-SEQUENCE:7")
        appendLine("#EXTINF:4.0,")
        appendLine(segment)
        appendLine("#EXT-X-ENDLIST")
    }.toByteArray()

    private fun startProxy(originUrl: String): Request.Builder {
        val server = ProxyServer(OkHttpClient()).also { proxy = it }
        server.start(
            sessionToken = "instrumented",
            host = "127.0.0.1",
            remuxEnabled = false,
            unwrapWrapperPlaylists = true,
            flattenHlsToStream = true,
        )
        return Request.Builder().url(server.buildLocalUrl(server.registerPlaylist(originUrl)))
    }

    /**
     * The redirect. An IPTV `.m3u8` answering 302 to a tokenised CDN path is the norm, and every
     * segment URI in the body that comes back is relative to where it came from - not to what was
     * asked for. Built on the wrong base, `a.ts` lands in the wrong directory and the origin 404s
     * it; a refused segment is deliberately skipped, so the renderer would get a connection that
     * stays open and carries nothing.
     */
    @Test
    fun aRedirectedPlaylistResolvesItsSegmentsAgainstWhereItLanded() {
        val origin = Origin().also { this.origin = it }
        origin.redirect("/live.m3u8", "/cdn/v.m3u8")
        origin.route("/cdn/v.m3u8", "application/vnd.apple.mpegurl", mediaPlaylist("a.ts"))
        origin.route("/cdn/a.ts", "video/mp2t", tsBytes())

        val request = startProxy(origin.urlFor("/live.m3u8")).build()

        client.newCall(request).execute().use { response ->
            assertEquals("video/mp2t", response.header("Content-Type"))
            assertEquals(tsBytes().size, response.body.bytes().size)
        }
        assertEquals("the segment was fetched from the wrong base", 1, origin.hitsFor("/cdn/a.ts"))
        assertEquals("a URL built on the pre-redirect base was requested", 0, origin.hitsFor("/a.ts"))
    }

    /**
     * A channel listed but not deliverable must give the route back. Committing the response to
     * "video/mp2t" before a single byte is known to exist leaves the renderer holding an endless
     * empty body, with the manifest route - which might have worked - already unreachable.
     */
    @Test
    fun aChannelThatCanServeNoSegmentsFallsBackToTheManifest() {
        val origin = Origin().also { this.origin = it }
        origin.route("/live.m3u8", "application/vnd.apple.mpegurl", mediaPlaylist("gone.ts"))

        val request = startProxy(origin.urlFor("/live.m3u8")).build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            assertTrue("expected the manifest as a fallback, got:\n${body.take(BODY_PREVIEW)}", body.contains("#EXTM3U"))
        }
    }

    /**
     * A HEAD has to arrive at all, and then has to be true.
     *
     * Both halves failed on this path. The bytes sat in a `BufferedOutputStream` that nothing ever
     * flushed while the socket was closed underneath it, so the renderer got a connection that
     * opened and shut with nothing on it; and the answer, once it arrived, was "video/mp2t" for
     * every channel whether or not the GET could produce one.
     */
    @Test
    fun aHeadOnAFlattenableChannelAnnouncesTheStreamAndPullsNoMedia() {
        val origin = Origin().also { this.origin = it }
        origin.route("/live.m3u8", "application/vnd.apple.mpegurl", mediaPlaylist("a.ts"))
        origin.route("/a.ts", "video/mp2t", tsBytes())

        val request = startProxy(origin.urlFor("/live.m3u8")).head().build()

        client.newCall(request).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
            assertEquals("video/mp2t", response.header("Content-Type"))
        }
        assertEquals("a HEAD must not pull any media", 0, origin.hitsFor("/a.ts"))
    }

    /** And the other side of it: a channel that cannot be flattened must name the manifest the GET
     * will really serve, or the renderer commits to MPEG-TS and is handed an M3U8. */
    @Test
    fun aHeadOnAChannelThatCannotBeFlattenedNamesTheManifest() {
        val origin = Origin().also { this.origin = it }
        val encrypted = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:4")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\"")
            appendLine("#EXTINF:4,")
            appendLine("a.ts")
            appendLine("#EXT-X-ENDLIST")
        }
        origin.route("/live.m3u8", "application/vnd.apple.mpegurl", encrypted.toByteArray())

        val request = startProxy(origin.urlFor("/live.m3u8")).head().build()

        client.newCall(request).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
            assertEquals("application/vnd.apple.mpegurl", response.header("Content-Type"))
            assertFalse("a HEAD carries no body", response.body.bytes().isNotEmpty())
        }
    }

    private companion object {
        const val TS_PACKET_SIZE = 188
        const val TS_PACKETS = 40
        const val HEAD_BUFFER_BYTES = 2048
        const val TIMEOUT_SECONDS = 10L
        const val BODY_PREVIEW = 200
        const val HTTP_OK = 200
    }
}
