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
private const val BYTE_MASK = 0xFF
private const val PID_HIGH_MASK = 0x1F
private const val BYTE_SHIFT = 8
private const val PAYLOAD_UNIT_START_MASK = 0x40
private const val ADAPTATION_FIELD_CONTROL_OFFSET = 3
private const val ADAPTATION_FIELD_CONTROL_SHIFT = 4
private const val ADAPTATION_FIELD_CONTROL_MASK = 0x03
private const val ADAPTATION_ONLY = 0b10
private const val ADAPTATION_AND_PAYLOAD = 0b11
private const val PAYLOAD_ONLY = 0b01
private const val TS_HEADER_SIZE = 4
private const val ADAPTATION_PAYLOAD_PREFIX_SIZE = 5
private const val ADAPTATION_FLAGS_OFFSET = 5
private const val PCR_PRESENT_FLAG = 0x10
private const val RANDOM_ACCESS_FLAG = 0x40
private const val MIN_PCR_ADAPTATION_LENGTH = 7
private const val PCR_BYTE_0_OFFSET = 6
private const val PCR_BYTE_1_OFFSET = 7
private const val PCR_BYTE_2_OFFSET = 8
private const val PCR_BYTE_3_OFFSET = 9
private const val PCR_BYTE_4_OFFSET = 10
private const val PCR_BYTE_0_SHIFT = 25
private const val PCR_BYTE_1_SHIFT = 17
private const val PCR_BYTE_2_SHIFT = 9
private const val PCR_BYTE_4_SHIFT = 7
private const val SECTION_HEADER_SIZE = 3
private const val SECTION_LENGTH_HIGH_MASK = 0x0F
private const val CRC_SIZE = 4
private const val PAT_TABLE_ID = 0x00
private const val PAT_PROGRAMS_OFFSET = 8
private const val PAT_PROGRAM_ENTRY_SIZE = 4
private const val PAT_PROGRAM_NUMBER_LOW_OFFSET = 1
private const val PAT_PID_HIGH_OFFSET = 2
private const val PAT_PID_LOW_OFFSET = 3
private const val PMT_TABLE_ID = 0x02
private const val PMT_FIXED_HEADER_SIZE = 12
private const val PMT_PROGRAM_INFO_LENGTH_HIGH_OFFSET = 10
private const val PMT_PROGRAM_INFO_LENGTH_LOW_OFFSET = 11
private const val PMT_ES_ENTRY_HEADER_SIZE = 5
private const val PMT_ES_PID_HIGH_OFFSET = 1
private const val PMT_ES_PID_LOW_OFFSET = 2
private const val PMT_ES_INFO_LENGTH_HIGH_OFFSET = 3
private const val PMT_ES_INFO_LENGTH_LOW_OFFSET = 4
private const val PCR_PID_HIGH_OFFSET = 8
private const val PCR_PID_LOW_OFFSET = 9

private const val STREAM_TYPE_MPEG1_VIDEO = 0x01
private const val STREAM_TYPE_MPEG2_VIDEO = 0x02
private const val STREAM_TYPE_MPEG4_VIDEO = 0x10
private const val STREAM_TYPE_H264 = 0x1B
private const val STREAM_TYPE_MVC = 0x20
private const val STREAM_TYPE_HEVC = 0x24
private const val STREAM_TYPE_AVS = 0x42

private const val PCR_CLOCK_HZ = 90_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val DEFAULT_TARGET_DURATION_MILLIS = 5_000L
private const val SEGMENT_DURATION_MULTIPLIER = 2
private const val DEFAULT_STARTUP_SEGMENT_COUNT = 2
private const val DEFAULT_STARTUP_TARGET_DURATION_MILLIS = 2_000L

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
    /** Feed one 188-byte packet living at `data[offset, offset + 188)` - callers that already hold
     * a larger buffer (e.g. a raw network read) pass it directly rather than copying the packet out
     * first, since this runs once per TS packet, the hottest call in the whole raw-TS-remux path. */
    fun feed(data: ByteArray, offset: Int = 0): TsSegment?
    fun flush(): TsSegment?
    fun onReconnect(): TsSegment?
}

// The PCR base field is 33 bits, so it wraps roughly every 26.5 hours - see wrapAwareDelta.
private const val PCR_BASE_MODULUS = 1L shl 33

// A small set of common video stream_types, matching core/cast/TsProgramInfoParser's video category -
// duplicated rather than shared because this walks a live per-packet stream to find just the PID
// (for keyframe detection), not a one-shot buffer to classify a whole program's codecs.
private val VIDEO_STREAM_TYPES = setOf(
    STREAM_TYPE_MPEG1_VIDEO,
    STREAM_TYPE_MPEG2_VIDEO,
    STREAM_TYPE_MPEG4_VIDEO,
    STREAM_TYPE_H264,
    STREAM_TYPE_MVC,
    STREAM_TYPE_HEVC,
    STREAM_TYPE_AVS,
)

/**
 * Cuts a continuous raw MPEG-TS elementary stream into HLS-style segments, aligned to video
 * keyframes (the adaptation field's random_access_indicator on the video PID, discovered from
 * PAT -> PMT the same way as [com.uacastplayer.core.cast.TsProgramInfoParser]) so each segment is
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
    private val targetDurationMillis: Long = DEFAULT_TARGET_DURATION_MILLIS,
    private val maxSegmentDurationMillis: Long = targetDurationMillis * SEGMENT_DURATION_MULTIPLIER,
    private val maxSegmentBytes: Int = DEFAULT_MAX_SEGMENT_BYTES,
    private val startupSegments: Int = DEFAULT_STARTUP_SEGMENT_COUNT,
    private val startupTargetDurationMillis: Long = DEFAULT_STARTUP_TARGET_DURATION_MILLIS,
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

    /** Feed one 188-byte TS packet at `data[offset, offset + 188)` (anything else is ignored).
     * Returns a completed [TsSegment] when this packet lands on a cut point, in which case the
     * packet itself starts the *next* segment (it is never dropped). */
    override fun feed(data: ByteArray, offset: Int): TsSegment? {
        // `offset > data.size - PACKET_SIZE`, not `offset + PACKET_SIZE > data.size`: the addition
        // overflows, and an overflowed sum is negative, so the guard passed and the read below threw
        // `Index 2147483646 out of bounds for length 188`. The subtraction cannot overflow - both
        // sides are non-negative and it only ever goes down - and it stays correct for a buffer
        // shorter than one packet, where the right-hand side is simply negative.
        // `||` short-circuits, so the read is only reached once the bounds are known good.
        if (offset < 0 ||
            offset > data.size - PACKET_SIZE ||
            (data[offset].toInt() and BYTE_MASK) != SYNC_BYTE
        ) {
            return null
        }
        val pid = pidOf(data, offset)
        discoverProgramInfo(pid, data, offset)
        val pcr = readPcr(pid, data, offset)

        // Elapsed time is measured up to and including this packet's own PCR (falling back to the
        // last one seen if this packet doesn't carry one) - a keyframe packet's own timestamp is
        // what decides whether *it* is a valid cut point, not the packet before it.
        val elapsed = elapsedMillis(pcr ?: lastPcrTicks)
        // `pid == videoPid` short-circuits isKeyframeStart(data, offset) for every other PID - a
        // broadcast routinely carries other PIDs (teletext, DVB subtitles) with PES structures this
        // segmenter has no business parsing, and some real-world feeds send persistently malformed
        // PES on those specific PIDs. Content on a non-video PID must never be inspected for a
        // keyframe flag at all, regardless of what its bytes happen to look like - see
        // TsSegmenterTest's "garbage PID" regression test.
        val isKeyframeBoundary = videoPid != null && pid == videoPid && isKeyframeStart(data, offset)
        val shouldCut = shouldCut(isKeyframeBoundary, elapsed)

        val completed = if (shouldCut) completeSegment() else null
        if (shouldCut) segmentStartPcrTicks = pcr ?: lastPcrTicks

        buffer.write(data, offset, PACKET_SIZE)
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
            minOf(
                maxSegmentDurationMillis,
                startupTargetDurationMillis * SEGMENT_DURATION_MULTIPLIER,
            )
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
        val elapsedTicks = segmentStartPcrTicks?.let { start ->
            referenceTicks?.let { reference -> wrapAwareDelta(start, reference) }
        }
        return elapsedTicks?.let { it * MILLIS_PER_SECOND / PCR_CLOCK_HZ } ?: 0
    }

    private fun discoverProgramInfo(pid: Int, data: ByteArray, offset: Int) {
        if (pmtPid == null && pid == PAT_PID) {
            pmtPid = parsePatFirstProgramPid(data, offset)
            return
        }
        if (pmtPid == null || pid != pmtPid) return
        if (videoPid == null) {
            videoPid = parsePmtFirstVideoPid(data, offset)
        }
        if (!pcrPidResolved) {
            val parsedPcrPid = parsePmtPcrPid(data, offset)
            if (parsedPcrPid != null) {
                pcrPidResolved = true
                pcrPid = if (parsedPcrPid == PCR_PID_NONE) null else parsedPcrPid
            }
        }
    }

    private fun readPcr(pid: Int, data: ByteArray, offset: Int): Long? {
        val expectedPid = if (pcrPidResolved) pid == pcrPid else videoPid == null || pid == videoPid
        val length = adaptationFieldLength(data, offset)
        return if (!expectedPid || length == null || length < MIN_PCR_ADAPTATION_LENGTH) {
            null
        } else {
            val flags = data[offset + ADAPTATION_FLAGS_OFFSET].toInt() and BYTE_MASK
            if ((flags and PCR_PRESENT_FLAG) != 0) readPcrBase(data, offset) else null
        }
    }

    private fun readPcrBase(data: ByteArray, offset: Int): Long {
        val b0 = data[offset + PCR_BYTE_0_OFFSET].toLong() and BYTE_MASK.toLong()
        val b1 = data[offset + PCR_BYTE_1_OFFSET].toLong() and BYTE_MASK.toLong()
        val b2 = data[offset + PCR_BYTE_2_OFFSET].toLong() and BYTE_MASK.toLong()
        val b3 = data[offset + PCR_BYTE_3_OFFSET].toLong() and BYTE_MASK.toLong()
        val b4 = data[offset + PCR_BYTE_4_OFFSET].toLong() and BYTE_MASK.toLong()
        return (b0 shl PCR_BYTE_0_SHIFT) or
            (b1 shl PCR_BYTE_1_SHIFT) or
            (b2 shl PCR_BYTE_2_SHIFT) or
            (b3 shl 1) or
            (b4 shr PCR_BYTE_4_SHIFT)
    }
}

private fun pidOf(data: ByteArray, offset: Int): Int {
    val b1 = data[offset + 1].toInt() and BYTE_MASK
    val b2 = data[offset + 2].toInt() and BYTE_MASK
    return ((b1 and PID_HIGH_MASK) shl BYTE_SHIFT) or b2
}

private fun payloadUnitStart(data: ByteArray, offset: Int): Boolean =
    (data[offset + 1].toInt() and PAYLOAD_UNIT_START_MASK) != 0

/** null if this packet carries no adaptation field at all (payload-only). */
private fun adaptationFieldLength(data: ByteArray, offset: Int): Int? {
    val adaptationFieldControl =
        (data[offset + ADAPTATION_FIELD_CONTROL_OFFSET].toInt() shr ADAPTATION_FIELD_CONTROL_SHIFT) and
            ADAPTATION_FIELD_CONTROL_MASK
    if (adaptationFieldControl != ADAPTATION_ONLY &&
        adaptationFieldControl != ADAPTATION_AND_PAYLOAD
    ) {
        return null
    }
    return data[offset + TS_HEADER_SIZE].toInt() and BYTE_MASK
}

private fun isKeyframeStart(data: ByteArray, offset: Int): Boolean {
    val length = adaptationFieldLength(data, offset)
    return if (payloadUnitStart(data, offset) && length != null && length > 0) {
        val flags = data[offset + ADAPTATION_FLAGS_OFFSET].toInt() and BYTE_MASK
        (flags and RANDOM_ACCESS_FLAG) != 0
    } else {
        false
    }
}

/** Returns the section payload with the adaptation field and pointer_field already skipped - the
 * one remaining per-packet copy in this file, but only PAT/PMT packets (a couple total, not per
 * packet) ever reach this far. */
private fun sectionPayload(data: ByteArray, offset: Int): ByteArray? {
    val packetEnd = offset + PACKET_SIZE
    val adaptationFieldControl =
        (data[offset + ADAPTATION_FIELD_CONTROL_OFFSET].toInt() shr ADAPTATION_FIELD_CONTROL_SHIFT) and
            ADAPTATION_FIELD_CONTROL_MASK
    val payloadStart = when (adaptationFieldControl) {
        ADAPTATION_AND_PAYLOAD ->
            offset + ADAPTATION_PAYLOAD_PREFIX_SIZE + (data[offset + TS_HEADER_SIZE].toInt() and BYTE_MASK)
        PAYLOAD_ONLY -> offset + TS_HEADER_SIZE
        else -> null
    }
    return payloadStart
        ?.takeIf { it < packetEnd }
        ?.let { start -> start + 1 + (data[start].toInt() and BYTE_MASK) }
        ?.takeIf { it < packetEnd }
        ?.let { data.copyOfRange(it, packetEnd) }
}

private fun sectionLength(section: ByteArray): Int {
    if (section.size < SECTION_HEADER_SIZE) return 0
    val b1 = section[1].toInt() and BYTE_MASK
    val b2 = section[2].toInt() and BYTE_MASK
    return ((b1 and SECTION_LENGTH_HIGH_MASK) shl BYTE_SHIFT) or b2
}

private fun parsePatFirstProgramPid(data: ByteArray, offset: Int): Int? {
    val section = sectionPayload(data, offset)
        ?.takeIf { it.isNotEmpty() && (it[0].toInt() and BYTE_MASK) == PAT_TABLE_ID }
    return section?.let(::firstProgramPid)
}

private fun firstProgramPid(section: ByteArray): Int? {
    val length = sectionLength(section)
    val programsEnd = SECTION_HEADER_SIZE + length - CRC_SIZE
    // `cursor`, not another `offset`: the parameter of that name indexes the 188-byte PACKET, this
    // indexes the extracted SECTION. They shadowed each other, which in a byte parser is a
    // one-character edit away from an invisible off-by-one against the wrong buffer.
    var cursor = PAT_PROGRAMS_OFFSET
    while (cursor + PAT_PROGRAM_ENTRY_SIZE <= programsEnd &&
        cursor + PAT_PROGRAM_ENTRY_SIZE <= section.size
    ) {
        val programNumber = ((section[cursor].toInt() and BYTE_MASK) shl BYTE_SHIFT) or
            (section[cursor + PAT_PROGRAM_NUMBER_LOW_OFFSET].toInt() and BYTE_MASK)
        val pid = (
            (section[cursor + PAT_PID_HIGH_OFFSET].toInt() and PID_HIGH_MASK) shl BYTE_SHIFT
            ) or (section[cursor + PAT_PID_LOW_OFFSET].toInt() and BYTE_MASK)
        if (programNumber != 0) return pid
        cursor += PAT_PROGRAM_ENTRY_SIZE
    }
    return null
}

private fun parsePmtFirstVideoPid(data: ByteArray, offset: Int): Int? {
    // Must fit the fixed PMT header through program_info_length (bytes 0-11, see below) before any
    // of it can be read - see TsProgramInfoParser.parsePmtStreamTypes for the same guard.
    val section = sectionPayload(data, offset)
        ?.takeIf { it.size >= PMT_FIXED_HEADER_SIZE && (it[0].toInt() and BYTE_MASK) == PMT_TABLE_ID }
    return section?.let(::firstVideoPid)
}

private fun firstVideoPid(section: ByteArray): Int? {
    val length = sectionLength(section)
    val programInfoLength = (
        (section[PMT_PROGRAM_INFO_LENGTH_HIGH_OFFSET].toInt() and SECTION_LENGTH_HIGH_MASK) shl BYTE_SHIFT
        ) or (section[PMT_PROGRAM_INFO_LENGTH_LOW_OFFSET].toInt() and BYTE_MASK)
    // See parsePatFirstProgramPid for why this is not called `offset` too.
    var cursor = PMT_FIXED_HEADER_SIZE + programInfoLength
    val sectionEnd = SECTION_HEADER_SIZE + length - CRC_SIZE
    while (cursor + PMT_ES_ENTRY_HEADER_SIZE <= sectionEnd &&
        cursor + PMT_ES_ENTRY_HEADER_SIZE <= section.size
    ) {
        val streamType = section[cursor].toInt() and BYTE_MASK
        val esPid = (
            (section[cursor + PMT_ES_PID_HIGH_OFFSET].toInt() and PID_HIGH_MASK) shl BYTE_SHIFT
            ) or (section[cursor + PMT_ES_PID_LOW_OFFSET].toInt() and BYTE_MASK)
        val esInfoLength = (
            (section[cursor + PMT_ES_INFO_LENGTH_HIGH_OFFSET].toInt() and SECTION_LENGTH_HIGH_MASK) shl
                BYTE_SHIFT
            ) or (section[cursor + PMT_ES_INFO_LENGTH_LOW_OFFSET].toInt() and BYTE_MASK)
        if (streamType in VIDEO_STREAM_TYPES) return esPid
        cursor += PMT_ES_ENTRY_HEADER_SIZE + esInfoLength
    }
    return null
}

/** Returns the raw PCR_PID field (13 bits, bytes 8-9 of the section) - may be [PCR_PID_NONE].
 * Returns null only if this packet isn't a parseable PMT section at all (caller must not treat
 * that as "no PCR", since a later packet could still resolve it - see [TsSegmenter.pcrPidResolved]). */
private fun parsePmtPcrPid(data: ByteArray, offset: Int): Int? {
    val section = sectionPayload(data, offset)
        ?.takeIf { it.size >= PCR_PID_FIELD_END && (it[0].toInt() and BYTE_MASK) == PMT_TABLE_ID }
        ?: return null
    return ((section[PCR_PID_HIGH_OFFSET].toInt() and PID_HIGH_MASK) shl BYTE_SHIFT) or
        (section[PCR_PID_LOW_OFFSET].toInt() and BYTE_MASK)
}

/** [PCR_BASE_MODULUS]-aware subtraction: a raw `last - start` goes hugely negative the instant
 * the 33-bit PCR counter wraps mid-stream, which would otherwise make every segment after a wrap
 * look like it has negative duration. */
internal fun wrapAwareDelta(start: Long, last: Long): Long {
    val raw = last - start
    return if (raw < 0) raw + PCR_BASE_MODULUS else raw
}
