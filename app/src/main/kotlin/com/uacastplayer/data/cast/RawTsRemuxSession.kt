package com.uacastplayer.data.cast

import com.uacastplayer.log.AppLog
import com.uacastplayer.proxy.LiveHlsPlaylistBuilder
import com.uacastplayer.proxy.RemuxReconnectPolicy
import com.uacastplayer.proxy.RemuxSegmentBuffer
import com.uacastplayer.proxy.TsPacketSegmenter
import com.uacastplayer.proxy.TsSegment
import com.uacastplayer.proxy.TsSegmenter
import java.io.InputStream
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Response

private const val TAG = "RawTsRemuxSession"
private const val REMUX_TARGET_SEGMENT_SECONDS = 5
// Long enough to cover a slow origin's first startup segment (a keyframe-less broadcast only cuts
// at the startup force-cut ceiling, ~4s of stream time, plus connect/read overhead) - an EMPTY
// initial playlist makes the receiver give up outright, which is strictly worse than it waiting a
// few extra seconds on its first manifest request.
private const val REMUX_INITIAL_PLAYLIST_WAIT_MILLIS = 8_000L
private const val REMUX_POLL_INTERVAL_MILLIS = 100L
private const val REMUX_READ_CHUNK_BYTES = 64 * 1024
private const val REMUX_READER_JOIN_TIMEOUT_MILLIS = 1_000L
private const val TS_PACKET_SIZE = 188
private const val TS_SYNC_BYTE = 0x47

/**
 * Owns one continuous upstream raw-TS read for the lifetime of a "raw TS remux" channel (see
 * docs/PROXY_RULES.md "Raw TS remux"): a single background thread reads the origin response body
 * in chunks, resyncs to TS packet boundaries the same way [com.uacastplayer.cast.TsProgramInfoParser]
 * does (a live HTTP stream isn't guaranteed to start exactly on a sync byte), feeds each packet to
 * a [TsSegmenter], and appends completed segments to a [RemuxSegmentBuffer] that [ProxyServer]
 * serves segment/playlist requests from. [initialResponse] is this session's own to close - callers
 * must not wrap it in their own `use {}`. If the connection drops mid-stream, the reader reconnects
 * to the same origin via [httpClient] with backoff (see [RemuxReconnectPolicy]) instead of ending
 * the session outright - see docs/PROXY_RULES.md "Raw TS remux" for the reconnect/discontinuity
 * behavior.
 */
internal class RawTsRemuxSession(
    val resourceId: String,
    initialResponse: Response,
    private val httpClient: OkHttpClient,
    private val segmentUrl: (resourceId: String, sequence: Int) -> String,
    private val segmenter: TsPacketSegmenter = TsSegmenter(targetDurationMillis = REMUX_TARGET_SEGMENT_SECONDS * 1000L),
) {
    private val buffer = RemuxSegmentBuffer()
    private val bufferLock = Any()
    @Volatile private var running = true
    private var readerThread: Thread? = null

    /** True once [readLoop] has exited for good - reconnect policy gave up, natural stream end, or
     * [stop] - meaning [currentPlaylist] is frozen and will never grow again. [ProxyServer] checks
     * this before serving an existing session's playlist: a dead session must be replaced with a
     * fresh one, not polled forever (see servePlaylistOrMediaResource). Buffered segments are still
     * servable after this flips - only the playlist short-circuit cares. */
    @Volatile var hasEnded = false
        private set

    // Only ever read/written from the reader thread itself - readLoop() owns it exclusively once
    // start() has been called, so it doesn't need the same synchronization as `buffer`.
    private var currentResponse = initialResponse

    fun start() {
        readerThread = thread(name = "ProxyServer-remux-$resourceId") { readLoop() }
    }

    /** Stops the reader loop and closes the upstream connection - safe to call from any thread,
     * including the reader thread itself (e.g. on natural stream end, where it's a no-op beyond
     * the close). */
    fun stop() {
        running = false
        runCatching { currentResponse.close() } // unblocks a blocking read() inside readLoop()
        val thread = readerThread
        thread?.interrupt() // unblocks a Thread.sleep() during a reconnect backoff wait
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(REMUX_READER_JOIN_TIMEOUT_MILLIS) }
        }
    }

    /** Blocks the calling (request-handling) thread briefly for the first segment(s) to become
     * available, so the receiver's very first playlist fetch isn't an empty, useless list - then
     * returns whatever is ready, even if that's still nothing (a slow origin shouldn't hang the
     * connection forever). */
    fun awaitInitialPlaylist(): String {
        val deadline = System.currentTimeMillis() + REMUX_INITIAL_PLAYLIST_WAIT_MILLIS
        while (shouldAwaitMoreSegments(deadline)) {
            Thread.sleep(REMUX_POLL_INTERVAL_MILLIS)
        }
        return currentPlaylist()
    }

    /** [hasEnded] means no further segment can ever arrive (the final flush happens before the
     * flag flips), so once it's set whatever the buffer holds now is all there will ever be -
     * waiting out the rest of the deadline would just stall the request thread for nothing. */
    private fun shouldAwaitMoreSegments(deadline: Long): Boolean {
        if (!running || hasEnded) return false
        return isBufferEmpty() && System.currentTimeMillis() < deadline
    }

    fun currentPlaylist(): String {
        val snapshot = synchronized(bufferLock) { buffer.snapshot() }
        // One line per receiver playlist poll - shows whether the live window is actually growing
        // between polls (segments being produced) and what range the receiver gets to pick from.
        AppLog.d(TAG) {
            "Remux $resourceId playlist poll: ${snapshot.size} segments" +
                " [${snapshot.firstOrNull()?.sequence}..${snapshot.lastOrNull()?.sequence}]"
        }
        return LiveHlsPlaylistBuilder.build(
            segments = snapshot,
            segmentUrl = { sequence -> segmentUrl(resourceId, sequence) },
            configuredTargetDurationSeconds = REMUX_TARGET_SEGMENT_SECONDS,
        )
    }

    fun segmentBytes(sequence: Int): ByteArray? = synchronized(bufferLock) { buffer.segment(sequence)?.bytes }

    private fun isBufferEmpty(): Boolean = synchronized(bufferLock) { buffer.isEmpty }

    /** Reads from [currentResponse] until it ends (EOF, a dropped connection, or an uncaught
     * parsing error - see [readUntilDisconnected]), then - while still [running] - reconnects and
     * keeps going, rather than treating any of those as the end of the session. Only gives up once
     * [RemuxReconnectPolicy] does. */
    private fun readLoop() {
        var carry = ByteArray(0)
        var shouldContinue = true
        while (running && shouldContinue) {
            val input = currentResponse.body?.byteStream()
            if (input != null) {
                carry = readUntilDisconnected(input, carry)
                runCatching { currentResponse.close() }
                shouldContinue = running && reconnect()
                if (shouldContinue) carry = ByteArray(0) // stale trailing partial-packet bytes
            } else {
                AppLog.w(TAG) { "Raw TS remux for $resourceId: upstream response had no body" }
                shouldContinue = false
            }
        }
        // Idempotent last-resort close: normally currentResponse is already closed above, but a
        // stop() that lands while attemptReconnect is mid-flight can leave the freshly swapped-in
        // response unclosed (stop() closed the OLD one, the loop then exits on !running before
        // ever reading the new one) - field-confirmed as OkHttp connection-leak warnings against
        // the upstream segment hosts.
        runCatching { currentResponse.close() }
        segmenter.flush()?.let(::addSegment)
        hasEnded = true
    }

    /** Feeds [input] to the segmenter until it hits EOF, or *any* exception - not just an
     * IOException - reading or parsing this chunk. This is deliberately broad: [segmenter] is a
     * raw-byte parser fed directly from an arbitrary third-party server, and a corrupted packet
     * getting past [TsProgramInfoParser]/[TsSegmenter]'s own bounds checks must end this read
     * cycle and fall through to [reconnect], not crash the process (see [reconnect]'s caller,
     * [readLoop]) - the same guarantee an IOException from a dropped socket already got.
     * [TsSegmenter.feed] only mutates its internal buffer *after* it has finished parsing a packet
     * (see its source), so an exception thrown mid-parse never leaves it with a half-written
     * buffer - reusing the same [segmenter] instance across this catch and the next connection
     * attempt is safe, unlike a genuine reconnect where the PCR clock itself resets.
     * Returns the trailing incomplete packet bytes to prepend to the next read.
     *
     * [workBuffer] holds one read chunk plus room for the largest possible carry-over (a trailing
     * partial packet, always under [TS_PACKET_SIZE]) - each read() lands directly after the
     * existing carry and [consumePackets] shifts any new trailing partial packet back to the front
     * in place, so this allocates once per connection instead of the `carry + chunk.copyOf(read)`
     * this replaced, which allocated twice per network chunk for the lifetime of every cast
     * session - the single biggest GC-pressure contributor found in a static allocation audit. */
    @Suppress("TooGenericExceptionCaught")
    private fun readUntilDisconnected(input: InputStream, initialCarry: ByteArray): ByteArray {
        val workBuffer = ByteArray(REMUX_READ_CHUNK_BYTES + TS_PACKET_SIZE)
        var carryLength = initialCarry.size
        System.arraycopy(initialCarry, 0, workBuffer, 0, carryLength)
        try {
            while (running) {
                val read = input.read(workBuffer, carryLength, REMUX_READ_CHUNK_BYTES)
                if (read == -1) return workBuffer.copyOf(carryLength)
                carryLength = consumePackets(workBuffer, carryLength + read)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, e) { "Raw TS remux reader for $resourceId lost the connection or hit a parsing error" }
        }
        return workBuffer.copyOf(carryLength)
    }

    /** Backoff-retries the upstream connection (see [RemuxReconnectPolicy]) and marks the next
     * segment produced as a discontinuity on success. Returns false once the policy gives up or
     * [stop] is called mid-backoff - the caller should end the session in that case. */
    private fun reconnect(): Boolean {
        segmenter.onReconnect()?.let(::addSegment)
        var attempt = 0
        var reconnected = false
        var giveUp = false
        while (running && !reconnected && !giveUp) {
            when (val decision = RemuxReconnectPolicy.onDisconnected(attempt)) {
                is RemuxReconnectPolicy.Decision.Retry -> {
                    attempt = decision.nextAttempt
                    reconnected = attemptReconnect(decision.delayMillis, attempt)
                    giveUp = !reconnected && !running
                }
                RemuxReconnectPolicy.Decision.GiveUp -> {
                    AppLog.w(TAG) { "Raw TS remux for $resourceId: giving up after repeated reconnect failures" }
                    giveUp = true
                }
            }
        }
        return reconnected
    }

    /** One reconnect attempt: waits out the backoff delay, then re-issues the same origin request.
     * Returns false both when the request fails and when [stop] interrupts the backoff wait -
     * [reconnect]'s loop tells those apart via [running], which [stop] clears first. */
    private fun attemptReconnect(delayMillis: Long, attempt: Int): Boolean {
        if (!sleepUnlessInterrupted(delayMillis)) return false
        val newResponse = runCatching { httpClient.newCall(currentResponse.request).execute() }.getOrNull()
        val usable = newResponse != null && newResponse.isSuccessful && newResponse.body != null
        if (usable) {
            currentResponse = requireNotNull(newResponse)
            AppLog.d(TAG) { "Raw TS remux for $resourceId: reconnected upstream" }
        } else {
            runCatching { newResponse?.close() }
            AppLog.w(TAG) { "Raw TS remux for $resourceId: reconnect attempt $attempt failed" }
        }
        return usable
    }

    /** Sleeps for [delayMillis], returning false (instead of throwing) if interrupted - e.g. [stop]
     * was called mid-backoff - so [reconnect]'s own control flow can stay a flat single-return. */
    private fun sleepUnlessInterrupted(delayMillis: Long): Boolean = try {
        Thread.sleep(delayMillis)
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    /** Extracts every complete, sync-aligned packet from `data[0, length)` (byte-scanning forward
     * to resync after any misaligned byte, same idea as [com.uacastplayer.cast.TsProgramInfoParser]),
     * feeding each to [segmenter] directly at its offset - no per-packet copy, see [TsSegmenter.feed].
     * Shifts any trailing incomplete packet to the front of [data] in place and returns its length,
     * ready for the next read to land right after it. */
    private fun consumePackets(data: ByteArray, length: Int): Int {
        var offset = 0
        while (offset + TS_PACKET_SIZE <= length) {
            if ((data[offset].toInt() and 0xFF) == TS_SYNC_BYTE) {
                segmenter.feed(data, offset)?.let(::addSegment)
                offset += TS_PACKET_SIZE
            } else {
                offset++
            }
        }
        val remaining = length - offset
        if (remaining > 0) System.arraycopy(data, offset, data, 0, remaining)
        return remaining
    }

    private fun addSegment(segment: TsSegment) {
        synchronized(bufferLock) { buffer.add(segment) }
        AppLog.d(TAG) {
            "Remux $resourceId segment cut: seq=${segment.sequence} ${segment.bytes.size}B" +
                " ${segment.durationMillis}ms disc=${segment.discontinuity}"
        }
    }
}
