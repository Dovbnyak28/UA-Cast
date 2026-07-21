package com.uacastplayer.proxy

import java.io.ByteArrayOutputStream

/** [durationMillis] is the wall-clock span (from the stream's own PCR clock, not local time)
 * covered by this segment - used both for the segment's `#EXTINF` value and to derive
 * `#EXT-X-TARGETDURATION` (see [LiveHlsPlaylistBuilder]). [discontinuity] marks the first segment
 * produced after an upstream reconnect (see [TsSegmenter.onReconnect]) - the PCR/timestamp clock
 * is not continuous across the gap, which [LiveHlsPlaylistBuilder] must signal to the receiver via
 * `#EXT-X-DISCONTINUITY`. */
data class TsSegment(
    val sequence: Int,
    val bytes: ByteArray,
    val durationMillis: Long,
    val discontinuity: Boolean = false,
)

private const val PACKET_SIZE = 188
private const val SYNC_BYTE = 0x47
private const val PAT_PID = 0x0000
private const val PCR_CLOCK_HZ = 90_000L

// PMT's PCR_PID field uses this reserved value to mean "no PCR carried for this program" - see
// ISO/IEC 13818-1. Once seen, [TsSegmenter] stops treating any PID as a PCR source at all, rather
// than falling back to "any PID", since the stream has explicitly declared it has none.
private const val PCR_PID_NONE = 0x1FFF

// PCR_PID occupies section bytes 8-9, so the section must be at least 10 bytes to contain it.
private const val PCR_PID_FIELD_END = 10

private const val DEFAULT_MAX_SEGMENT_BYTES = 4 * 1024 * 1024

/** Seam letting [com.uacastplayer.data.cast.RawTsRemuxSession] accept a substitute in tests (e.g.
 * one that throws from [feed] to exercise that session's crash-recovery path) - [TsSegmenter] is
 * the only production implementation. */
interface TsPacketSegmenter {
    fun feed(packet: ByteArray): TsSegment?
    fun flush(): TsSegment?
    fun onReconnect(): TsSegment?
}

// The PCR base field is 33 bits, so it wraps roughly every 26.5 hours - see wrapAwareDelta.
private const val PCR_BASE_MODULUS = 1L shl 33

// A small set of common video stream_types, matching cast/TsProgramInfoParser's video category -
// duplicated rather than shared because this walks a live per-packet stream to find just the PID
// (for keyframe detection), not a one-shot buffer to classify a whole program's codecs.
private val VIDEO_STREAM_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x20, 0x24, 0x42)

/**
 * Cuts a continuous raw MPEG-TS elementary stream into HLS-style segments, aligned to video
 * keyframes (the adaptation field's random_access_indicator on the video PID, discovered from
 * PAT -> PMT the same way as [com.uacastplayer.cast.TsProgramInfoParser]) so each segment is
 * independently decodable - exactly what a real HLS segmenter produces, just done here instead of
 * at the origin. Segments are cut at the first keyframe at or after [targetDurationMillis] of
 * stream time (measured from the segment's own PCR, not wall-clock, since network jitter must not
 * affect segment duration accounting). If no keyframe shows up - PMT never resolves the video PID,
 * or a broadcast that never sets the flag - a segment is still force-cut at
 * [maxSegmentDurationMillis] so the buffer this feeds ([RemuxSegmentBuffer]) never sees an
 * unbounded segment; see docs/PROXY_RULES.md "Raw TS remux" for when that fallback matters.
 *
 * The PCR clock itself is read from the PMT's declared PCR_PID once the PMT has been seen, not
 * assumed to be on the video PID - a real broadcast is free to carry PCR on a separate PID (or on
 * audio), and treating the video PID as the PCR source in that case makes elapsed time freeze
 * forever, which either starves segmentation (buffer grows unbounded) or force-cuts every single
 * packet. Independent of the clock, [maxSegmentBytes] force-cuts a segment the instant it would
 * exceed that size, so a segment can never grow unbounded regardless of what the PCR is doing.
 *
 * The first [startupSegments] segments use [startupTargetDurationMillis] instead of
 * [targetDurationMillis] - a receiver's very first playlist fetch has to wait for that many
 * segments to exist at all (see `RawTsRemuxSession.awaitInitialPlaylist`), so ramping those first
 * few segments shorter gets a cast session playing noticeably sooner, at the cost of slightly
 * smaller (and thus more numerous) segments only at the very start of the channel.
 */
class TsSegmenter(
    private val targetDurationMillis: Long = 5_000,
    private val maxSegmentDurationMillis: Long = targetDurationMillis * 2,
    private val maxSegmentBytes: Int = DEFAULT_MAX_SEGMENT_BYTES,
    private val startupSegments: Int = 2,
    private val startupTargetDurationMillis: Long = 2_000,
) : TsPacketSegmenter {
    private var pmtPid: Int? = null
    private var videoPid: Int? = null

    // PCR_PID from the PMT - see [PCR_PID_NONE]. [pcrPidResolved] is tracked separately from
    // [pcrPid] because "PMT not seen yet" and "PMT seen, and it declared no PCR at all" both leave
    // pcrPid null but call for different readPcr behavior (fall back to video PID vs. accept PCR
    // from nowhere).
    private var pcrPid: Int? = null
    private var pcrPidResolved = false

    private var buffer = ByteArrayOutputStream()
    private var segmentStartPcrTicks: Long? = null
    private var lastPcrTicks: Long? = null
    private var nextSequence = 0
    private var pendingDiscontinuity = false

    /** Feed one 188-byte TS packet (anything else is ignored). Returns a completed [TsSegment]
     * when this packet lands on a cut point, in which case the packet itself starts the *next*
     * segment (it is never dropped). */
    override fun feed(packet: ByteArray): TsSegment? {
        if (packet.size != PACKET_SIZE || (packet[0].toInt() and 0xFF) != SYNC_BYTE) return null
        val pid = pidOf(packet)
        discoverProgramInfo(pid, packet)
        val pcr = readPcr(pid, packet)

        // Elapsed time is measured up to and including this packet's own PCR (falling back to the
        // last one seen if this packet doesn't carry one) - a keyframe packet's own timestamp is
        // what decides whether *it* is a valid cut point, not the packet before it.
        val elapsed = elapsedMillis(pcr ?: lastPcrTicks)
        // `pid == videoPid` short-circuits isKeyframeStart(packet) for every other PID - a broadcast
        // routinely carries other PIDs (teletext, DVB subtitles) with PES structures this segmenter
        // has no business parsing, and some real-world feeds send persistently malformed PES on
        // those specific PIDs. Content on a non-video PID must never be inspected for a keyframe
        // flag at all, regardless of what its bytes happen to look like - see TsSegmenterTest's
        // "garbage PID" regression test.
        val isKeyframeBoundary = videoPid != null && pid == videoPid && isKeyframeStart(packet)
        val shouldCut = shouldCut(isKeyframeBoundary, elapsed)

        val completed = if (shouldCut) completeSegment() else null
        if (shouldCut) segmentStartPcrTicks = pcr ?: lastPcrTicks

        buffer.write(packet)
        if (pcr != null) {
            if (segmentStartPcrTicks == null) segmentStartPcrTicks = pcr
            lastPcrTicks = pcr
        }
        return completed
    }

    /** Emits whatever's been buffered so far as a final (possibly short) segment - call once the
     * upstream connection ends. Returns null if nothing was buffered. */
    override fun flush(): TsSegment? = if (buffer.size() > 0) completeSegment() else null

    /**
     * Call when the upstream connection has just been re-established after a drop (see
     * `RawTsRemuxSession`'s reconnect logic). Flushes whatever was buffered before the gap as its
     * own final (non-discontinuous) segment, then resets the PCR clock - the reconnected stream's
     * PCR values are on a different clock than before the gap, so diffing against pre-gap ticks
     * would produce a garbage (or negative) duration. PAT/PMT/videoPid/pcrPid are deliberately left
     * alone: it's the same channel/program, just a new TCP connection to it. The *next* segment
     * produced by [feed] is marked [TsSegment.discontinuity] so [LiveHlsPlaylistBuilder] can signal
     * the gap to the receiver.
     */
    override fun onReconnect(): TsSegment? {
        val flushed = flush()
        segmentStartPcrTicks = null
        lastPcrTicks = null
        pendingDiscontinuity = true
        return flushed
    }

    private fun shouldCut(isKeyframeBoundary: Boolean, elapsed: Long): Boolean =
        buffer.size() > 0 &&
            ((isKeyframeBoundary && elapsed >= effectiveTargetDurationMillis()) ||
                elapsed >= effectiveMaxSegmentDurationMillis() ||
                buffer.size() >= maxSegmentBytes)

    /** [nextSequence] is the sequence number the segment currently being built will get once it
     * completes, so this is "am I still building one of the first [startupSegments] segments". */
    private fun effectiveTargetDurationMillis(): Long =
        if (nextSequence < startupSegments) startupTargetDurationMillis else targetDurationMillis

    /** The force-cut ceiling must ramp down with the startup target too: a broadcast that never
     * flags keyframes only ever cuts at this ceiling, and a first segment that takes the full
     * steady-state [maxSegmentDurationMillis] to appear starves the receiver's very first playlist
     * fetch (see `RawTsRemuxSession.awaitInitialPlaylist`) - confirmed in the field as an empty
     * initial playlist and the receiver silently giving up. Same 2x ratio as the steady-state
     * default, never above the configured ceiling. */
    private fun effectiveMaxSegmentDurationMillis(): Long =
        if (nextSequence < startupSegments) {
            minOf(maxSegmentDurationMillis, startupTargetDurationMillis * 2)
        } else {
            maxSegmentDurationMillis
        }

    private fun completeSegment(): TsSegment {
        val segment = TsSegment(nextSequence, buffer.toByteArray(), elapsedMillis(lastPcrTicks), pendingDiscontinuity)
        pendingDiscontinuity = false
        buffer = ByteArrayOutputStream()
        nextSequence++
        return segment
    }

    private fun elapsedMillis(referenceTicks: Long?): Long {
        val start = segmentStartPcrTicks ?: return 0
        val reference = referenceTicks ?: return 0
        return wrapAwareDelta(start, reference) * 1000 / PCR_CLOCK_HZ
    }

    private fun discoverProgramInfo(pid: Int, packet: ByteArray) {
        if (pmtPid == null && pid == PAT_PID) {
            pmtPid = parsePatFirstProgramPid(packet)
            return
        }
        if (pmtPid == null || pid != pmtPid) return
        if (videoPid == null) {
            videoPid = parsePmtFirstVideoPid(packet)
        }
        if (!pcrPidResolved) {
            val parsedPcrPid = parsePmtPcrPid(packet)
            if (parsedPcrPid != null) {
                pcrPidResolved = true
                pcrPid = if (parsedPcrPid == PCR_PID_NONE) null else parsedPcrPid
            }
        }
    }

    private fun readPcr(pid: Int, packet: ByteArray): Long? {
        if (pcrPidResolved) {
            if (pid != pcrPid) return null
        } else if (videoPid != null && pid != videoPid) {
            return null
        }
        val length = adaptationFieldLength(packet) ?: return null
        if (length < 7) return null
        val flags = packet[5].toInt() and 0xFF
        if ((flags and 0x10) == 0) return null // PCR_flag
        val b6 = packet[6].toLong() and 0xFF
        val b7 = packet[7].toLong() and 0xFF
        val b8 = packet[8].toLong() and 0xFF
        val b9 = packet[9].toLong() and 0xFF
        val b10 = packet[10].toLong() and 0xFF
        return (b6 shl 25) or (b7 shl 17) or (b8 shl 9) or (b9 shl 1) or (b10 shr 7)
    }
}

private fun pidOf(packet: ByteArray): Int {
    val b1 = packet[1].toInt() and 0xFF
    val b2 = packet[2].toInt() and 0xFF
    return ((b1 and 0x1F) shl 8) or b2
}

private fun payloadUnitStart(packet: ByteArray): Boolean = (packet[1].toInt() and 0x40) != 0

/** null if this packet carries no adaptation field at all (payload-only). */
private fun adaptationFieldLength(packet: ByteArray): Int? {
    val adaptationFieldControl = (packet[3].toInt() shr 4) and 0x03
    if (adaptationFieldControl != 0b10 && adaptationFieldControl != 0b11) return null
    return packet[4].toInt() and 0xFF
}

private fun isKeyframeStart(packet: ByteArray): Boolean {
    if (!payloadUnitStart(packet)) return false
    val length = adaptationFieldLength(packet) ?: return false
    if (length == 0) return false
    val flags = packet[5].toInt() and 0xFF
    return (flags and 0x40) != 0 // random_access_indicator
}

/** Returns the section payload with the adaptation field and pointer_field already skipped. */
private fun sectionPayload(packet: ByteArray): ByteArray? {
    val adaptationFieldControl = (packet[3].toInt() shr 4) and 0x03
    var offset = 4
    when (adaptationFieldControl) {
        0b10 -> return null
        0b11 -> {
            val adaptationLength = packet[offset].toInt() and 0xFF
            offset += 1 + adaptationLength
        }
        0b01 -> Unit
        else -> return null
    }
    if (offset >= packet.size) return null
    val pointerField = packet[offset].toInt() and 0xFF
    offset += 1 + pointerField
    if (offset >= packet.size) return null
    return packet.copyOfRange(offset, packet.size)
}

private fun sectionLength(section: ByteArray): Int {
    if (section.size < 3) return 0
    val b1 = section[1].toInt() and 0xFF
    val b2 = section[2].toInt() and 0xFF
    return ((b1 and 0x0F) shl 8) or b2
}

private fun parsePatFirstProgramPid(packet: ByteArray): Int? {
    val section = sectionPayload(packet) ?: return null
    if (section.isEmpty() || (section[0].toInt() and 0xFF) != 0x00) return null
    val length = sectionLength(section)
    val programsEnd = 3 + length - 4
    var offset = 8
    while (offset + 4 <= programsEnd && offset + 4 <= section.size) {
        val programNumber = ((section[offset].toInt() and 0xFF) shl 8) or (section[offset + 1].toInt() and 0xFF)
        val pid = ((section[offset + 2].toInt() and 0x1F) shl 8) or (section[offset + 3].toInt() and 0xFF)
        if (programNumber != 0) return pid
        offset += 4
    }
    return null
}

private fun parsePmtFirstVideoPid(packet: ByteArray): Int? {
    val section = sectionPayload(packet) ?: return null
    // Must fit the fixed PMT header through program_info_length (bytes 0-11, see below) before any
    // of it can be read - see TsProgramInfoParser.parsePmtStreamTypes for the same guard.
    if (section.size < 12 || (section[0].toInt() and 0xFF) != 0x02) return null
    val length = sectionLength(section)
    val programInfoLength = ((section[10].toInt() and 0x0F) shl 8) or (section[11].toInt() and 0xFF)
    var offset = 12 + programInfoLength
    val sectionEnd = 3 + length - 4
    while (offset + 5 <= sectionEnd && offset + 5 <= section.size) {
        val streamType = section[offset].toInt() and 0xFF
        val esPid = ((section[offset + 1].toInt() and 0x1F) shl 8) or (section[offset + 2].toInt() and 0xFF)
        val esInfoLength = ((section[offset + 3].toInt() and 0x0F) shl 8) or (section[offset + 4].toInt() and 0xFF)
        if (streamType in VIDEO_STREAM_TYPES) return esPid
        offset += 5 + esInfoLength
    }
    return null
}

/** Returns the raw PCR_PID field (13 bits, bytes 8-9 of the section) - may be [PCR_PID_NONE].
 * Returns null only if [packet] isn't a parseable PMT section at all (caller must not treat that
 * as "no PCR", since a later packet could still resolve it - see [TsSegmenter.pcrPidResolved]). */
private fun parsePmtPcrPid(packet: ByteArray): Int? {
    val section = sectionPayload(packet)
        ?.takeIf { it.size >= PCR_PID_FIELD_END && (it[0].toInt() and 0xFF) == 0x02 }
        ?: return null
    return ((section[8].toInt() and 0x1F) shl 8) or (section[9].toInt() and 0xFF)
}

/** [PCR_BASE_MODULUS]-aware subtraction: a raw `last - start` goes hugely negative the instant
 * the 33-bit PCR counter wraps mid-stream, which would otherwise make every segment after a wrap
 * look like it has negative duration. */
internal fun wrapAwareDelta(start: Long, last: Long): Long {
    val raw = last - start
    return if (raw < 0) raw + PCR_BASE_MODULUS else raw
}
