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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The remux route: an endless raw MPEG-TS turned into a live HLS playlist on the phone.
 *
 * This is what the app does for a receiver that will only take HLS when the origin hands out a bare
 * transport stream - the opposite direction to [ProxyFlattenInstrumentedTest], which turns HLS back
 * into a stream for a receiver that will only take one. Between them they are the two ways this app
 * rewrites a channel for a device that cannot read what the provider serves, and both had only ever
 * run on a desktop JVM.
 *
 * The stream here carries no PCR, so segments are cut by [com.uacastplayer.proxy.TsSegmenter]'s byte
 * ceiling rather than by its clock. That is deliberate: the byte ceiling is the fallback that has to
 * hold for a provider whose timestamps are broken or absent, and it is the branch a timing-based
 * fixture would never reach.
 */
class ProxyRemuxInstrumentedTest {

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

    /**
     * Serves a long raw transport stream with no `Content-Length`, closing the socket when done -
     * which is how an endless live stream is delimited, and how the session learns it ended.
     */
    private class Origin(private val body: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val hits = AtomicInteger(0)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/live.ts"

        fun hits(): Int = hits.get()

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

        /**
         * The write is guarded, and it has to be: a live stream's reader is *expected* to hang up
         * mid-body - that is what stopping a cast does, and this session is stopped in `@After`
         * while nine megabytes are still going out. An unguarded `socketWrite` then throws on a
         * pool thread, which the executor rethrows as an `Error` and the whole instrumentation
         * process dies, taking every other test in the class with it.
         */
        private fun serve(client: Socket) {
            client.use {
                it.getInputStream().read(ByteArray(REQUEST_BUFFER_BYTES))
                hits.incrementAndGet()
                try {
                    val out = it.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\n".toByteArray())
                    // In chunks rather than one write, so a reader that left is noticed in the next
                    // few kilobytes instead of after the whole stream has been pushed at a dead
                    // socket - which is also how a real origin behaves.
                    var offset = 0
                    while (offset < body.size) {
                        val length = minOf(WRITE_CHUNK_BYTES, body.size - offset)
                        out.write(body, offset, length)
                        offset += length
                    }
                    out.flush()
                } catch (_: IOException) {
                    // The reader hung up. Nothing to do and nothing to report.
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    /** Well-formed enough for the sniffer and the segmenter alike: a sync byte every 188. No PCR,
     * so the byte ceiling is what cuts. */
    private fun tsStream(bytes: Int): ByteArray =
        ByteArray(bytes) { if (it % TS_PACKET_SIZE == 0) 0x47.toByte() else (it % 251).toByte() }

    private fun startRemuxingProxy(): Pair<ProxyServer, String> {
        val origin = Origin(tsStream(STREAM_BYTES)).also { this.origin = it }
        val server = ProxyServer(OkHttpClient()).also { proxy = it }
        server.start(
            sessionToken = SESSION,
            host = "127.0.0.1",
            remuxEnabled = true,
            unwrapWrapperPlaylists = true,
            flattenHlsToStream = false,
        )
        return server to server.buildLocalUrl(server.registerPlaylist(origin.url))
    }

    private fun get(url: String): Pair<Int, String> =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.code to it.body.string() }

    /**
     * A bare stream becomes a playlist, and the segments in it are this proxy's own URLs.
     *
     * Both halves matter. A receiver that only reads HLS needs the manifest; a manifest whose
     * segments still pointed at the origin would send the receiver back to the very stream it
     * could not read.
     */
    @Test
    fun aRawStreamIsServedAsAPlaylistOfLocalSegments() {
        val (_, url) = startRemuxingProxy()

        val playlist = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals("application/vnd.apple.mpegurl", response.header("Content-Type"))
            response.body.string()
        }

        assertTrue("expected a live HLS playlist, got:\n${playlist.take(BODY_PREVIEW)}", playlist.contains("#EXTM3U"))
        val segments = playlist.lines().filter { it.startsWith("http://") }
        assertTrue("the playlist listed no segments:\n${playlist.take(BODY_PREVIEW)}", segments.isNotEmpty())
        segments.forEach {
            assertTrue("a segment was not rewritten back through the proxy: $it", it.contains("/hls/$SESSION/"))
            assertTrue("a segment url is not a remux segment: $it", it.endsWith(".ts"))
        }
    }

    /** And the segments are fetchable, aligned, and typed as transport stream - the bytes a
     * receiver actually decodes. */
    @Test
    fun aSegmentFromThePlaylistIsRealTransportStream() {
        val (_, url) = startRemuxingProxy()
        val playlist = get(url).second
        val segmentUrl = playlist.lines().first { it.startsWith("http://") }

        client.newCall(Request.Builder().url(segmentUrl).build()).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
            assertEquals("video/MP2T", response.header("Content-Type"))
            val bytes = response.body.bytes()
            assertTrue("the segment is empty", bytes.isNotEmpty())
            assertEquals("a segment must be a whole number of TS packets", 0, bytes.size % TS_PACKET_SIZE)
            assertEquals("a segment must start on a sync byte", 0x47.toByte(), bytes[0])
        }
    }

    /** A HEAD on a segment answers with the same headers and no body - a receiver sizing a segment
     * before fetching it must get a real Content-Length, and this path writes one explicitly. */
    @Test
    fun aHeadOnASegmentCarriesItsLengthAndNoBody() {
        val (_, url) = startRemuxingProxy()
        val segmentUrl = get(url).second.lines().first { it.startsWith("http://") }
        val declared = client.newCall(Request.Builder().url(segmentUrl).build()).execute()
            .use { it.body.bytes().size }

        client.newCall(Request.Builder().url(segmentUrl).head().build()).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
            assertEquals(declared.toString(), response.header("Content-Length"))
            assertEquals("a HEAD must carry no body", 0, response.body.bytes().size)
        }
    }

    /**
     * A segment that rolled off the live window, or never existed, is a 404 rather than an empty
     * 200 - the receiver retries a 404 and stalls forever on an empty success.
     */
    @Test
    fun aSegmentThatNeverExistedIsFourOhFour() {
        val (_, url) = startRemuxingProxy()
        val segmentUrl = get(url).second.lines().first { it.startsWith("http://") }
        val invented = segmentUrl.substringBeforeLast('/') + "/seg99999.ts"

        assertEquals(HTTP_NOT_FOUND, get(invented).first)
    }

    /**
     * A receiver polls the playlist every few seconds for the whole cast. Each poll must be served
     * from the session already running, not by opening a second connection to the origin - many
     * IPTV providers allow exactly one, so a second fetch does not merely waste bandwidth, it gets
     * refused and takes the first one down with it.
     */
    @Test
    fun pollingThePlaylistDoesNotOpenASecondUpstreamConnection() {
        val (_, url) = startRemuxingProxy()
        val origin = checkNotNull(origin)

        get(url)
        val afterFirst = origin.hits()
        repeat(EXTRA_POLLS) { get(url) }

        assertEquals("each playlist poll re-fetched the origin", afterFirst, origin.hits())
    }

    private companion object {
        const val SESSION = "instrumented"
        const val TS_PACKET_SIZE = 188

        /** Past the segmenter's 4MB byte ceiling twice over, so there is a cut and something after
         * it - a single half-filled buffer would prove nothing about segmenting. */
        const val STREAM_BYTES = 9 * 1024 * 1024

        const val REQUEST_BUFFER_BYTES = 2048
        const val WRITE_CHUNK_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 30L
        const val BODY_PREVIEW = 300
        const val EXTRA_POLLS = 3
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
    }
}
