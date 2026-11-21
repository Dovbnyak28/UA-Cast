package com.uacastplayer.data.cast

import com.uacastplayer.proxy.TsPacketSegmenter
import com.uacastplayer.proxy.TsSegment
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PROGRAM_NUMBER = 1
private const val PMT_PID = 0x100
private const val VIDEO_PID = 0x101
private const val PCR_HZ = 90_000L

private fun tsPacket(pid: Int, section: ByteArray): ByteArray {
    val packet = ByteArray(188) { 0xFF.toByte() }
    packet[0] = 0x47.toByte()
    packet[1] = (0x40 or ((pid shr 8) and 0x1F)).toByte()
    packet[2] = (pid and 0xFF).toByte()
    packet[3] = 0x10
    packet[4] = 0x00
    section.copyInto(packet, destinationOffset = 5)
    return packet
}

private fun buildPatSection(): ByteArray {
    val programLoop = byteArrayOf(
        (PROGRAM_NUMBER shr 8).toByte(), (PROGRAM_NUMBER and 0xFF).toByte(),
        (0xE0 or ((PMT_PID shr 8) and 0x1F)).toByte(), (PMT_PID and 0xFF).toByte(),
    )
    val sectionLength = 5 + programLoop.size + 4
    val header = byteArrayOf(
        0x00,
        (0xB0 or ((sectionLength shr 8) and 0x0F)).toByte(), (sectionLength and 0xFF).toByte(),
        0x00, 0x01,
        0xC1.toByte(),
        0x00,
        0x00,
    )
    return header + programLoop + ByteArray(4)
}

private fun buildPmtSection(): ByteArray {
    val streamsBytes = byteArrayOf(
        0x1B,
        (0xE0 or ((VIDEO_PID shr 8) and 0x1F)).toByte(), (VIDEO_PID and 0xFF).toByte(),
        0xF0.toByte(), 0x00,
    )
    val sectionLength = 2 + 1 + 1 + 1 + 2 + 2 + streamsBytes.size + 4
    val header = byteArrayOf(
        0x02,
        (0xB0 or ((sectionLength shr 8) and 0x0F)).toByte(), (sectionLength and 0xFF).toByte(),
        (PROGRAM_NUMBER shr 8).toByte(), (PROGRAM_NUMBER and 0xFF).toByte(),
        0xC1.toByte(),
        0x00,
        0x00,
        (0xE0 or ((VIDEO_PID shr 8) and 0x1F)).toByte(), (VIDEO_PID and 0xFF).toByte(),
        0xF0.toByte(), 0x00,
    )
    return header + streamsBytes + ByteArray(4)
}

private fun videoPacket(keyframe: Boolean, pcrSeconds: Double): ByteArray {
    val pcrBase = (pcrSeconds * PCR_HZ).toLong()
    val packet = ByteArray(188) { 0xFF.toByte() }
    packet[0] = 0x47.toByte()
    packet[1] = (0x40 or ((VIDEO_PID shr 8) and 0x1F)).toByte()
    packet[2] = (VIDEO_PID and 0xFF).toByte()
    packet[3] = 0x30
    packet[4] = 7
    packet[5] = (if (keyframe) 0x50 else 0x10).toByte() // random_access_indicator + PCR_flag
    packet[6] = ((pcrBase shr 25) and 0xFF).toByte()
    packet[7] = ((pcrBase shr 17) and 0xFF).toByte()
    packet[8] = ((pcrBase shr 9) and 0xFF).toByte()
    packet[9] = ((pcrBase shr 1) and 0xFF).toByte()
    packet[10] = (((pcrBase and 1L) shl 7) or 0x7E).toByte()
    packet[11] = 0x00
    return packet
}

/** A raw TS byte stream shaped so the very first `feed()` call after the keyframe at 6s produces a
 * completed segment (target duration is a fixed 5s inside [RawTsRemuxSession]), then ends - a
 * finite in-memory body is enough to exercise the whole read/resync/segment/buffer pipeline
 * without a real socket, since the reader loop treats EOF exactly like a stream that just stopped. */
private fun syntheticTsStream(): ByteArray =
    tsPacket(0x0000, buildPatSection()) +
        tsPacket(PMT_PID, buildPmtSection()) +
        videoPacket(keyframe = true, pcrSeconds = 0.0) +
        videoPacket(keyframe = true, pcrSeconds = 6.0) +
        videoPacket(keyframe = true, pcrSeconds = 6.5)

private fun fakeResponse(bytes: ByteArray): Response =
    Response.Builder()
        .request(Request.Builder().url("https://origin.example/raw.ts").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(bytes.toResponseBody("video/mp2t".toMediaType()))
        .build()

/** Reproduces Okio AsyncTimeout's close/read race without relying on timing inside Okio itself:
 * read blocks until Response.close() reaches the source, then throws the same AssertionError that
 * AsyncTimeout can emit while stop() is concurrently tearing down a live response. */
private class ConcurrentCloseAssertionBody : ResponseBody() {
    val readStarted = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private val responseSource: BufferedSource = object : Source {
        override fun read(sink: Buffer, byteCount: Long): Long {
            readStarted.countDown()
            while (closed.count > 0L) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
            }
            throw AssertionError("simulated AsyncTimeout close/read race")
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed.countDown()
        }
    }.buffer()

    override fun contentType() = "video/mp2t".toMediaType()
    override fun contentLength(): Long = -1L
    override fun source(): BufferedSource = responseSource
}

private fun fakeResponse(body: ResponseBody): Response =
    Response.Builder()
        .request(Request.Builder().url("https://origin.example/raw.ts").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

class RawTsRemuxSessionTest {

    @Test
    fun `stop survives an AsyncTimeout assertion from a response closed during read`() {
        val body = ConcurrentCloseAssertionBody()
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(body),
            httpClient = OkHttpClient(),
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
        )

        session.start()
        assertTrue("reader never entered the response body", body.readStarted.await(5, TimeUnit.SECONDS))
        session.stop()
        session.awaitStopped()

        assertTrue("concurrent response close crashed the reader thread", session.hasEnded)
    }

    @Test
    fun `reads a raw TS body, cuts a segment at the keyframe past the target, and serves it back`() {
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = OkHttpClient(),
            segmentUrl = { resourceId, sequence -> "http://phone.local/hls/token/$resourceId/seg$sequence.ts" },
        )
        session.start()
        try {
            val playlist = session.awaitInitialPlaylist()
            assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:0"))
            assertTrue(playlist.contains("http://phone.local/hls/token/resource-1/seg0.ts"))

            val segmentBytes = session.segmentBytes(0)
            assertNotNull(segmentBytes)
            // PAT + PMT + the first keyframe packet, before the 6s keyframe triggers the cut.
            assertEquals(3 * 188, segmentBytes?.size)
        } finally {
            session.stop()
        }
    }

    @Test
    fun `stopping the upstream reader keeps completed segments servable for handoff`() {
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = OkHttpClient(),
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
        )
        session.start()
        session.awaitInitialPlaylist()

        session.stop()
        session.awaitStopped()

        assertNotNull(session.segmentBytes(0))
        assertTrue(session.currentPlaylist().contains("seg0.ts"))
    }

    /** Always throws from [feed] - stands in for the *next* not-yet-found bounds bug in
     * TsSegmenter's own byte parsing, to prove RawTsRemuxSession's reader thread survives it. */
    private class ThrowingSegmenter : TsPacketSegmenter {
        val reconnectCalled = CountDownLatch(1)

        @Suppress("TooGenericExceptionThrown")
        override fun feed(data: ByteArray, offset: Int): TsSegment? = throw RuntimeException("simulated parser bug")
        override fun flush(): TsSegment? = null
        override fun onReconnect(): TsSegment? {
            reconnectCalled.countDown()
            return null
        }
    }

    @Test
    fun `a feed() exception is caught and routed to reconnect instead of crashing the reader thread`() {
        val fakeSegmenter = ThrowingSegmenter()
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = OkHttpClient(),
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
            segmenter = fakeSegmenter,
        )
        session.start()
        try {
            // reconnect() is only ever reached once readUntilDisconnected's catch has swallowed the
            // RuntimeException from feed() - if it propagated instead, this thread would die
            // uncaught and onReconnect() would never fire.
            assertTrue(fakeSegmenter.reconnectCalled.await(5, TimeUnit.SECONDS))
        } finally {
            session.stop()
        }
    }

    @Test
    fun `hasEnded is false while the reader is alive and true once the session stops`() {
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = OkHttpClient(),
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
        )
        session.start()
        try {
            session.awaitInitialPlaylist()
            // The reader is still alive here (EOF sends it into reconnect backoff, not exit).
            assertEquals(false, session.hasEnded)
        } finally {
            session.stop()
        }
        // stop() only signals and unblocks the reader (it must never block its main-thread callers -
        // see its doc); awaitStopped() is what waits for the thread to actually unwind. readLoop's
        // single exit point sets the flag - the same exit the reconnect-give-up path goes through,
        // which is what ProxyServer's dead-session check actually exists for.
        session.awaitStopped()
        assertTrue(session.hasEnded)
    }

    @Test
    fun `stop cancels a reconnect that is still waiting for response headers`() {
        val reconnectStarted = CountDownLatch(1)
        val reconnectCancelled = CountDownLatch(1)
        val reconnectingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                reconnectStarted.countDown()
                while (!chain.call().isCanceled()) {
                    // Ignore the reader-thread interrupt deliberately. Classic socket I/O does;
                    // the regression being proved is that stop() cancels the Call itself.
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10))
                }
                reconnectCancelled.countDown()
                throw IOException("cancelled reconnect")
            }
            .build()
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = reconnectingClient,
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
        )

        session.start()
        assertTrue("reader never reached reconnect", reconnectStarted.await(5, TimeUnit.SECONDS))
        session.stop()

        assertTrue(
            "stopped remux left the reconnect Call active",
            reconnectCancelled.await(1, TimeUnit.SECONDS),
        )
        session.awaitStopped()
        assertTrue(session.hasEnded)
    }

    @Test
    fun `an unknown segment sequence resolves to null`() {
        val session = RawTsRemuxSession(
            resourceId = "resource-1",
            initialResponse = fakeResponse(syntheticTsStream()),
            httpClient = OkHttpClient(),
            segmentUrl = { _, sequence -> "seg$sequence.ts" },
        )
        session.start()
        try {
            session.awaitInitialPlaylist()
            assertEquals(null, session.segmentBytes(999))
        } finally {
            session.stop()
        }
    }
}
