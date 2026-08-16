package com.uacastplayer.data.cast

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The proxy as a receiver actually meets it: over a socket, on the phone that binds it.
 *
 * This is the one component in the app whose entire contract is with software nobody here wrote -
 * a Chromecast's Default Media Receiver, a TV's DLNA stack - reaching it over the LAN. Everything
 * it can get wrong is protocol-shaped: a status line, a header, a method it refuses, a connection
 * it closes early. None of that is meaningfully exercised by calling Kotlin functions.
 *
 * Loopback rather than the real Wi-Fi address, deliberately: the server binds `0.0.0.0` regardless
 * (see [ProxyHttpServer]), so loopback reaches the same socket, and a test that depended on the
 * device having an IP address would fail on a phone in airplane mode for reasons that are not
 * about this app.
 */
class ProxyServerInstrumentedTest {

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

    /** Records the method, path and Range of everything asked of it, so what the proxy *forwarded*
     * can be asserted rather than inferred from what came back. */
    private class Origin : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val routes = mutableMapOf<String, Pair<String, ByteArray>>()
        private val hits = mutableMapOf<String, AtomicInteger>()
        private val ranges = mutableListOf<String>()

        fun urlFor(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        fun hitsFor(path: String): Int = synchronized(hits) { hits[path]?.get() ?: 0 }

        fun rangesSeen(): List<String> = synchronized(ranges) { ranges.toList() }

        fun route(path: String, contentType: String, body: ByteArray) {
            synchronized(routes) { routes[path] = contentType to body }
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
                val head = ByteArray(REQUEST_BUFFER_BYTES)
                val read = it.getInputStream().read(head)
                val request = String(head, 0, maxOf(read, 0))
                val path = request.lineSequence().firstOrNull().orEmpty().split(' ').getOrNull(1).orEmpty()
                synchronized(hits) { hits.getOrPut(path.substringBefore('?')) { AtomicInteger(0) }.incrementAndGet() }
                Regex("(?im)^Range:\\s*([^\\r\\n]+)").find(request)?.let { m ->
                    synchronized(ranges) { ranges.add(m.groupValues[1].trim()) }
                }
                val entry = synchronized(routes) { routes[path.substringBefore('?')] }
                val out = it.getOutputStream()
                if (entry == null) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return@use
                }
                val (type, body) = entry
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\n" +
                        "Content-Length: ${body.size}\r\n\r\n").toByteArray(),
                )
                out.write(body)
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    private fun tsBytes(packets: Int = TS_PACKETS): ByteArray =
        ByteArray(packets * TS_PACKET_SIZE) { if (it % TS_PACKET_SIZE == 0) 0x47.toByte() else (it % 251).toByte() }

    private fun startProxy(remux: Boolean = false, unwrap: Boolean = true): ProxyServer {
        val server = ProxyServer(OkHttpClient()).also { proxy = it }
        server.start(
            sessionToken = SESSION,
            host = "127.0.0.1",
            remuxEnabled = remux,
            unwrapWrapperPlaylists = unwrap,
            flattenHlsToStream = false,
        )
        return server
    }

    /**
     * A receiver asking for a byte range must have it forwarded, not silently answered with the
     * whole file. Chromecast's media player seeks with `Range` on anything it thinks is seekable,
     * and a proxy that drops the header turns every seek into a re-download from zero.
     */
    @Test
    fun aRangeRequestIsForwardedToTheOrigin() {
        val origin = Origin().also { this.origin = it }
        origin.route("/movie.ts", "video/mp2t", tsBytes())
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist(origin.urlFor("/movie.ts")))

        client.newCall(Request.Builder().url(url).header("Range", "bytes=376-751").build()).execute().use {
            assertTrue("the proxy refused a ranged request outright", it.code in 200..299)
        }

        assertEquals(listOf("bytes=376-751"), origin.rangesSeen())
    }

    /**
     * The Default Media Receiver is a web app, so every fetch it makes is a cross-origin XHR and
     * its ranged segment requests are preflighted. A 405 here fails the media request that follows
     * before it is ever made - and the symptom is playback dying as IDLE/ERROR with `playedMs=0`
     * while the server's own log shows it behaving perfectly.
     */
    @Test
    fun aCorsPreflightIsAnsweredBeforeAnyMediaRequest() {
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist("http://127.0.0.1:1/x.ts"))

        val request = Request.Builder().url(url)
            .method("OPTIONS", null)
            .header("Access-Control-Request-Headers", "Range")
            .header("Origin", "https://www.gstatic.com")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(HTTP_NO_CONTENT, response.code)
            assertEquals("*", response.header("Access-Control-Allow-Origin"))
            assertTrue(
                "the preflight must echo the header it was asked about, got: ${response.header("Access-Control-Allow-Headers")}",
                response.header("Access-Control-Allow-Headers").orEmpty().contains("Range"),
            )
        }
    }

    /** Everything the receiver does is GET or HEAD. Anything else is not a receiver, and this
     * server sits on a LAN port for the length of a cast. */
    @Test
    fun anythingThatIsNotAGetOrHeadIsRefused() {
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist("http://127.0.0.1:1/x.ts"))

        val post = Request.Builder().url(url).post("hello".toRequestBody()).build()

        client.newCall(post).execute().use { assertEquals(HTTP_METHOD_NOT_ALLOWED, it.code) }
    }

    /**
     * A URL from a previous cast must stop working when a new session starts.
     *
     * The token in the path is what makes that true, and it is not decoration: without it a
     * receiver that kept a stale URL - they do, across a channel switch - would go on pulling a
     * stream through a session the user has ended.
     */
    @Test
    fun aUrlFromThePreviousSessionStopsWorking() {
        val origin = Origin().also { this.origin = it }
        origin.route("/live.ts", "video/mp2t", tsBytes())
        val server = startProxy()
        val staleUrl = server.buildLocalUrl(server.registerPlaylist(origin.urlFor("/live.ts")))

        client.newCall(Request.Builder().url(staleUrl).build()).execute().use {
            assertEquals(HTTP_OK, it.code)
        }
        val newPort = server.start(sessionToken = "a-different-session", host = "127.0.0.1")

        // Same port or not, the old path carries the old token and must be refused.
        val reissued = staleUrl.replace(Regex(":\\d+/"), ":$newPort/")
        client.newCall(Request.Builder().url(reissued).build()).execute().use {
            assertEquals("a stale session's url still served media", HTTP_NOT_FOUND, it.code)
        }
    }

    /** A resource this session never registered - an evicted LRU entry, a URL invented by
     * something scanning the LAN - is a 404 and not a crash on the pool thread. */
    @Test
    fun anUnknownResourceIsFourOhFour() {
        val server = startProxy()
        val real = server.buildLocalUrl(server.registerPlaylist("http://127.0.0.1:1/x.ts"))
        val invented = real.substringBeforeLast('/') + "/0123456789abcdef"

        client.newCall(Request.Builder().url(invented).build()).execute().use {
            assertEquals(HTTP_NOT_FOUND, it.code)
        }
    }

    /**
     * The IPTV "wrapper playlist": an m3u8 whose entire content is one pointer at an endless raw
     * stream, with no `#EXT-X-TARGETDURATION` to make it a real media playlist. Handing that to a
     * renderer as a manifest is what the Hisense refused - it has to be followed here, on the
     * phone, so what reaches the TV is continuous MPEG-TS.
     */
    @Test
    fun aWrapperPlaylistIsFollowedSoTheRendererGetsAStream() {
        val origin = Origin().also { this.origin = it }
        origin.route("/live.ts", "video/mp2t", tsBytes())
        origin.route(
            "/wrapper.m3u8",
            "application/vnd.apple.mpegurl",
            "#EXTM3U\n#EXTINF:-1,Channel\n${origin.urlFor("/live.ts")}\n".toByteArray(),
        )
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist(origin.urlFor("/wrapper.m3u8")))

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals("video/mp2t", response.header("Content-Type"))
            assertFalse("a manifest is the one thing this renderer cannot read", response.body.string().contains("#EXTM3U"))
        }
        assertEquals("the pointed-at stream was never fetched", 1, origin.hitsFor("/live.ts"))
    }

    /**
     * A real media playlist is rewritten so every segment is fetched back through the proxy, and
     * those rewritten URLs have to actually work - which is the half that a string assertion on the
     * manifest cannot reach.
     */
    @Test
    fun aRewrittenManifestsSegmentUrlsAreThemselvesServable() {
        val origin = Origin().also { this.origin = it }
        origin.route("/a.ts", "video/mp2t", tsBytes())
        val media = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:4")
            appendLine("#EXTINF:4.0,")
            appendLine(origin.urlFor("/a.ts"))
            appendLine("#EXT-X-ENDLIST")
        }
        origin.route("/media.m3u8", "application/vnd.apple.mpegurl", media.toByteArray())
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist(origin.urlFor("/media.m3u8")))

        val manifest = client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

        val segmentUrl = manifest.lines().first { it.startsWith("http://") && !it.startsWith("#") }
        assertTrue("the segment was not rewritten back through the proxy: $segmentUrl", segmentUrl.contains("/hls/$SESSION/"))
        client.newCall(Request.Builder().url(segmentUrl).build()).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
            assertEquals(tsBytes().size, response.body.bytes().size)
        }
        assertEquals(1, origin.hitsFor("/a.ts"))
    }

    /**
     * A receiver fetches segments while still polling the playlist, so more than one request is in
     * flight at once by design. The pool is sized for that (see [ProxyHttpServer]'s THREAD_POOL_SIZE
     * and the parked-handler reasoning above it); this asserts the shape holds on a real device
     * rather than only in the constant's comment.
     */
    @Test
    fun severalSimultaneousRequestsAreAllServed() {
        val origin = Origin().also { this.origin = it }
        origin.route("/a.ts", "video/mp2t", tsBytes())
        val server = startProxy()
        val url = server.buildLocalUrl(server.registerPlaylist(origin.urlFor("/a.ts")))
        val pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

        val results = (1..CONCURRENT_REQUESTS).map {
            pool.submit<Int> { client.newCall(Request.Builder().url(url).build()).execute().use { r -> r.code } }
        }.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        pool.shutdownNow()

        assertEquals(List(CONCURRENT_REQUESTS) { HTTP_OK }, results)
        assertEquals(CONCURRENT_REQUESTS, origin.hitsFor("/a.ts"))
    }

    private companion object {
        const val SESSION = "instrumented"
        const val TS_PACKET_SIZE = 188
        const val TS_PACKETS = 40
        const val REQUEST_BUFFER_BYTES = 4096
        const val TIMEOUT_SECONDS = 15L
        const val CONCURRENT_REQUESTS = 8
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}
