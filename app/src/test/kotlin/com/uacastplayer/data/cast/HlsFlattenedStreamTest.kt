package com.uacastplayer.data.cast

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one thing that tells the replay loop its owner has gone.
 *
 * [ProxyRendererProfileTest] covers the outcome through the real proxy, but it cannot isolate this:
 * `ProxyServer.stop()` also interrupts the pool thread, and that interrupt alone is enough to end
 * the loop at its next sleep. Measured, by removing the guard and watching that test still pass.
 *
 * So the guard is pinned here instead, with no interrupt anywhere near it - just an owner that
 * answers "no" and a live playlist that would otherwise be polled forever.
 */
class HlsFlattenedStreamTest {

    private var origin: Origin? = null
    private var redirecting: RedirectingOrigin? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @After
    fun tearDown() {
        origin?.close()
        redirecting?.close()
    }

    /**
     * Serves a live media playlist and one segment, counting what was asked for.
     *
     * [segmentPath] is what the playlist points at; only `/a.ts` is actually served, so passing
     * anything else gives a playlist whose segment this origin answers 404 to - a channel listed but
     * not deliverable, which is a real and common state for an IPTV origin.
     */
    private class Origin(private val segmentPath: String = "/a.ts") : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val hits = mutableMapOf<String, AtomicInteger>()

        fun urlFor(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        fun hitsFor(path: String): Int = synchronized(hits) { hits[path]?.get() ?: 0 }

        /** No `#EXT-X-ENDLIST`: genuinely live, so a loop with nothing to stop it polls forever. */
        private fun playlist() = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:1")
            appendLine("#EXT-X-MEDIA-SEQUENCE:1")
            appendLine("#EXTINF:1.0,")
            appendLine(urlFor(segmentPath))
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
                val head = ByteArray(2048)
                val read = it.getInputStream().read(head)
                val line = String(head, 0, maxOf(read, 0)).lineSequence().firstOrNull().orEmpty()
                val path = line.split(' ').getOrNull(1).orEmpty().substringBefore('?')
                synchronized(hits) { hits.getOrPut(path) { AtomicInteger(0) }.incrementAndGet() }
                val (status, type, body) = when (path) {
                    "/live.m3u8" -> Triple("200 OK", "application/vnd.apple.mpegurl", playlist().toByteArray())
                    "/a.ts" -> Triple("200 OK", "video/mp2t", ByteArray(SEGMENT_BYTES) { 0x47 })
                    else -> Triple("404 Not Found", "text/plain", ByteArray(0))
                }
                val out = it.getOutputStream()
                out.write(
                    "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(),
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

    private fun streamFor(origin: Origin, isRunning: () -> Boolean) = HlsFlattenedStream(
        httpClient = client,
        playlistUrl = origin.urlFor("/live.m3u8"),
        userAgent = "test",
        referrer = null,
        isRunning = isRunning,
    )

    /**
     * The guard. An owner that says stop after the first pass must end the loop with the playlist
     * fetched exactly once - and the first pass still served, since the renderer was owed it.
     */
    @Test
    fun `an owner that has gone ends the replay without another refresh`() {
        val origin = Origin().also { this.origin = it }
        var alive = true
        val stream = streamFor(origin) { alive.also { alive = false } }

        val wrote = stream.writeTo(ByteArrayOutputStream()) { }

        assertTrue("the first pass should still have been served", wrote)
        assertEquals("the playlist must not be refreshed after the owner has gone", 1, origin.hitsFor("/live.m3u8"))
        assertEquals(1, origin.hitsFor("/a.ts"))
        assertEquals(SEGMENT_BYTES.toLong(), stream.bytesWritten)
    }

    /**
     * The control, and what stops the assertion above from being satisfied by a loop that simply
     * never loops: with an owner that stays alive the same playlist really is polled again.
     */
    @Test
    fun `an owner that is still there keeps the replay going`() {
        val origin = Origin().also { this.origin = it }
        var passes = 0
        val stream = streamFor(origin) { passes++ < ALIVE_PASSES }

        stream.writeTo(ByteArrayOutputStream()) { }

        assertTrue(
            "expected repeated refreshes, got ${origin.hitsFor("/live.m3u8")}",
            origin.hitsFor("/live.m3u8") > 1,
        )
    }

    /**
     * Committing the response is what forecloses the fallback, so it may not happen on hope.
     *
     * The headers callback used to fire before the first segment was even requested. A channel whose
     * segments all fail - the origin dropped it, the token expired, or (until the test above) the
     * URLs were built on the wrong base - therefore produced a 200 with a valid content type and an
     * endless empty body, because a refused segment is deliberately skipped rather than treated as
     * the end of the channel. The renderer sits on that forever, and the manifest route that might
     * have worked is unreachable: the headers saying "this is a TS stream" have already gone out.
     *
     * Nothing here is an error, which is why it needed a test rather than a log line.
     */
    @Test
    fun `a channel that can serve no segments leaves the route open for the manifest`() {
        val origin = Origin(segmentPath = "/gone.ts").also { this.origin = it }
        var headers = 0
        // An owner that would allow several more passes. Correct code never reaches the second, so
        // the bound changes nothing about what is asserted - it is here only so that reintroducing
        // the bug makes this test *fail* rather than poll a dead channel forever.
        var passes = 0
        val stream = streamFor(origin) { passes++ < ALIVE_PASSES }

        val wrote = stream.writeTo(ByteArrayOutputStream()) { headers++ }

        assertTrue("the response was committed to a stream that carried nothing", !wrote)
        assertEquals("headers went out before a single byte was known to exist", 0, headers)
        assertEquals(0L, stream.bytesWritten)
        assertEquals("it should not have polled for a second pass", 1, origin.hitsFor("/live.m3u8"))
    }

    /**
     * A playlist that redirects, with its segment named relatively - the ordinary shape of an IPTV
     * origin, and the one the first [Origin] cannot express because it writes absolute segment URLs.
     *
     * `/live.m3u8` answers 302 to `/cdn/v.m3u8`, whose body names `a.ts`. Resolved against where the
     * body came from that is `/cdn/a.ts`; resolved against what was asked for it is `/a.ts`, which
     * this origin answers 404 to, exactly as a real one would.
     */
    private class RedirectingOrigin : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val hits = mutableMapOf<String, AtomicInteger>()

        fun urlFor(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        fun hitsFor(path: String): Int = synchronized(hits) { hits[path]?.get() ?: 0 }

        private fun playlist() = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-TARGETDURATION:1")
            appendLine("#EXT-X-MEDIA-SEQUENCE:1")
            appendLine("#EXTINF:1.0,")
            // Relative, which is what makes the base matter at all.
            appendLine("a.ts")
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
                val head = ByteArray(2048)
                val read = it.getInputStream().read(head)
                val line = String(head, 0, maxOf(read, 0)).lineSequence().firstOrNull().orEmpty()
                val path = line.split(' ').getOrNull(1).orEmpty().substringBefore('?')
                synchronized(hits) { hits.getOrPut(path) { AtomicInteger(0) }.incrementAndGet() }
                val out = it.getOutputStream()
                if (path == "/live.m3u8") {
                    out.write(
                        ("HTTP/1.1 302 Found\r\nLocation: /cdn/v.m3u8\r\nContent-Length: 0\r\n\r\n").toByteArray(),
                    )
                    out.flush()
                    return@use
                }
                val (status, type, body) = when (path) {
                    "/cdn/v.m3u8" -> Triple("200 OK", "application/vnd.apple.mpegurl", playlist().toByteArray())
                    "/cdn/a.ts" -> Triple("200 OK", "video/mp2t", ByteArray(SEGMENT_BYTES) { 0x47 })
                    else -> Triple("404 Not Found", "text/plain", ByteArray(0))
                }
                out.write(
                    "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(),
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

    /**
     * Segment URIs resolve against where the playlist came from, not against what was asked for.
     *
     * An IPTV `.m3u8` answering 302 to a tokenised CDN path is the norm rather than the exception,
     * and a master playlist's variants redirect almost always. Resolved against the requested URL a
     * relative `a.ts` becomes a URL on the wrong directory, and the origin 404s every segment - which
     * [HlsFlattenedStream.streamSegment] deliberately treats as a glitch worth skipping, so the
     * renderer gets a connection that stays open and plays nothing. There is no error anywhere: this
     * is the failure mode a DLNA user would report as "it just shows a black screen".
     *
     * Every other playlist path in this app already resolves against `response.request.url`; this
     * one did not.
     */
    @Test
    fun `a redirected playlist resolves its segments against where it landed`() {
        val origin = RedirectingOrigin().also { redirecting = it }
        var alive = true
        val stream = HlsFlattenedStream(
            httpClient = client,
            playlistUrl = origin.urlFor("/live.m3u8"),
            userAgent = "test",
            referrer = null,
            isRunning = { alive.also { alive = false } },
        )

        val wrote = stream.writeTo(ByteArrayOutputStream()) { }

        assertTrue("nothing was served at all", wrote)
        assertEquals("the segment was fetched from the wrong base", 1, origin.hitsFor("/cdn/a.ts"))
        assertEquals("a URL built on the pre-redirect base was requested", 0, origin.hitsFor("/a.ts"))
        assertEquals(SEGMENT_BYTES.toLong(), stream.bytesWritten)
    }

    private companion object {
        const val SEGMENT_BYTES = 188 * 4

        /** Enough passes to prove the loop came back for more, short enough to keep the test quick
         * at the 500ms refresh floor. */
        const val ALIVE_PASSES = 4
    }
}
