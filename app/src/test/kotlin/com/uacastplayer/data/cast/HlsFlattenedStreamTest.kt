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

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @After
    fun tearDown() {
        origin?.close()
    }

    /** Serves a live media playlist and one segment, counting what was asked for. */
    private class Origin : AutoCloseable {
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
            appendLine(urlFor("/a.ts"))
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
                val (type, body) = when (path) {
                    "/live.m3u8" -> "application/vnd.apple.mpegurl" to playlist().toByteArray()
                    "/a.ts" -> "video/mp2t" to ByteArray(SEGMENT_BYTES) { 0x47 }
                    else -> "text/plain" to ByteArray(0)
                }
                val out = it.getOutputStream()
                out.write(
                    "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(),
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

    private companion object {
        const val SEGMENT_BYTES = 188 * 4

        /** Enough passes to prove the loop came back for more, short enough to keep the test quick
         * at the 500ms refresh floor. */
        const val ALIVE_PASSES = 4
    }
}
