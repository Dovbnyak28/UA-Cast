package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PROGRAM_NUMBER = 1
private const val PMT_PID = 0x100
private const val VIDEO_PID = 0x101
private const val PCR_ONLY_PID = 0x102
private const val PCR_PID_NONE = 0x1FFF
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

private fun buildPatSection(programNumber: Int, pmtPid: Int): ByteArray {
    val programLoop = byteArrayOf(
        (programNumber shr 8).toByte(), (programNumber and 0xFF).toByte(),
        (0xE0 or ((pmtPid shr 8) and 0x1F)).toByte(), (pmtPid and 0xFF).toByte(),
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

private fun buildPmtSection(programNumber: Int, pcrPid: Int, streams: List<Pair<Int, Int>>): ByteArray {
    val streamsBytes = streams.flatMap { (type, pid) ->
        listOf(
            type.toByte(),
            (0xE0 or ((pid shr 8) and 0x1F)).toByte(), (pid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
        )
    }.toByteArray()
    val sectionLength = 2 + 1 + 1 + 1 + 2 + 2 + streamsBytes.size + 4
    val header = byteArrayOf(
        0x02,
        (0xB0 or ((sectionLength shr 8) and 0x0F)).toByte(), (sectionLength and 0xFF).toByte(),
        (programNumber shr 8).toByte(), (programNumber and 0xFF).toByte(),
        0xC1.toByte(),
        0x00,
        0x00,
        (0xE0 or ((pcrPid shr 8) and 0x1F)).toByte(), (pcrPid and 0xFF).toByte(),
        0xF0.toByte(), 0x00,
    )
    return header + streamsBytes + ByteArray(4)
}

private fun patPacket(): ByteArray = tsPacket(0x0000, buildPatSection(PROGRAM_NUMBER, PMT_PID))
private fun pmtPacket(pcrPid: Int = VIDEO_PID): ByteArray =
    tsPacket(PMT_PID, buildPmtSection(PROGRAM_NUMBER, pcrPid, listOf(0x1B to VIDEO_PID)))

/** A packet on an arbitrary PID carrying only a PCR in its adaptation field - no payload_unit_start,
 * no keyframe flag, since this stands in for a dedicated PCR PID (or an audio PID) that isn't the
 * video elementary stream. */
private fun pcrOnlyPacket(pid: Int, pcrBase: Long): ByteArray {
    val packet = ByteArray(188) { 0xFF.toByte() }
    packet[0] = 0x47.toByte()
    packet[1] = ((pid shr 8) and 0x1F).toByte()
    packet[2] = (pid and 0xFF).toByte()
    packet[3] = 0x30
    packet[4] = 7
    packet[5] = 0x10
    packet[6] = ((pcrBase shr 25) and 0xFF).toByte()
    packet[7] = ((pcrBase shr 17) and 0xFF).toByte()
    packet[8] = ((pcrBase shr 9) and 0xFF).toByte()
    packet[9] = ((pcrBase shr 1) and 0xFF).toByte()
    packet[10] = (((pcrBase and 1L) shl 7) or 0x7E).toByte()
    packet[11] = 0x00
    return packet
}

private fun videoPacket(pusi: Boolean, keyframe: Boolean, pcrBase: Long? = null): ByteArray {
    val packet = ByteArray(188) { 0xFF.toByte() }
    packet[0] = 0x47.toByte()
    packet[1] = ((if (pusi) 0x40 else 0x00) or ((VIDEO_PID shr 8) and 0x1F)).toByte()
    packet[2] = (VIDEO_PID and 0xFF).toByte()
    val hasAdaptation = keyframe || pcrBase != null
    packet[3] = (if (hasAdaptation) 0x30 else 0x10).toByte()
    if (hasAdaptation) {
        packet[4] = (if (pcrBase != null) 7 else 1).toByte()
        var flags = 0
        if (keyframe) flags = flags or 0x40
        if (pcrBase != null) flags = flags or 0x10
        packet[5] = flags.toByte()
        if (pcrBase != null) {
            packet[6] = ((pcrBase shr 25) and 0xFF).toByte()
            packet[7] = ((pcrBase shr 17) and 0xFF).toByte()
            packet[8] = ((pcrBase shr 9) and 0xFF).toByte()
            packet[9] = ((pcrBase shr 1) and 0xFF).toByte()
            packet[10] = (((pcrBase and 1L) shl 7) or 0x7E).toByte()
            packet[11] = 0x00
        }
    }
    return packet
}

private fun secondsToTicks(seconds: Double): Long = (seconds * PCR_HZ).toLong()

/** Builds a single 188-byte packet on [PMT_PID] whose section (after the payload-only adaptation
 * field and pointer_field are skipped) is exactly [sectionSize] bytes, table_id 0x02 (PMT) if any
 * bytes exist at all and everything else zeroed - a stand-in for a PMT cut short mid-broadcast. */
private fun truncatedPmtPacket(sectionSize: Int): ByteArray {
    val packet = ByteArray(188)
    packet[0] = 0x47.toByte()
    packet[1] = (0x40 or ((PMT_PID shr 8) and 0x1F)).toByte()
    packet[2] = (PMT_PID and 0xFF).toByte()
    packet[3] = 0x10
    val pointerField = (183 - sectionSize).coerceIn(0, 255)
    packet[4] = pointerField.toByte()
    val sectionStart = 5 + pointerField
    if (sectionSize > 0 && sectionStart < 188) packet[sectionStart] = 0x02
    return packet
}

private val TRUNCATED_SECTION_SIZES = listOf(0, 1, 5, 11, 12, 13)

class TsSegmenterTest {

    @Test
    fun `does not cut on a keyframe before the target duration`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, startupSegments = 0)
        assertNull(segmenter.feed(patPacket()))
        assertNull(segmenter.feed(pmtPacket()))
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0))))
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(2.0))))
    }

    @Test
    fun `cuts at the first keyframe at or after the target duration`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(3.0)))
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(4.5))))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(5.2)))
        assertEquals(0, completed?.sequence)
        // 5 packets buffered before the cut: PAT, PMT, and the three video packets fed so far.
        assertEquals(5 * 188, completed?.bytes?.size)
    }

    @Test
    fun `the cutting packet starts the next segment, not the completed one`() {
        val segmenter = TsSegmenter(targetDurationMillis = 1_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(1.5)))
        assertEquals(3 * 188, completed?.bytes?.size)
        val flushed = segmenter.flush()
        assertEquals(188, flushed?.bytes?.size)
        assertEquals(1, flushed?.sequence)
    }

    @Test
    fun `sequence numbers increase across multiple cuts`() {
        val segmenter = TsSegmenter(targetDurationMillis = 1_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        val first = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(1.1)))
        val second = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(2.2)))
        assertEquals(0, first?.sequence)
        assertEquals(1, second?.sequence)
    }

    @Test
    fun `force-cuts at the max duration when no keyframe is ever flagged`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, maxSegmentDurationMillis = 8_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(0.0)))
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(7.0))))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(9.0)))
        assertTrue(completed != null)
    }

    @Test
    fun `startup segments force-cut at twice the startup target when no keyframe is flagged`() {
        val segmenter = TsSegmenter(
            targetDurationMillis = 5_000,
            startupSegments = 2,
            startupTargetDurationMillis = 2_000,
        )
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(0.0)))
        // 3.5s is under the steady-state 10s ceiling but not yet at the 4s startup ceiling.
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(3.5))))
        // 4.2s crosses the startup ceiling (2 x 2s) - segment 0 must cut without any keyframe,
        // so the receiver's first playlist fetch isn't starved by a keyframe-less broadcast.
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(4.2)))
        assertEquals(0, completed?.sequence)
    }

    @Test
    fun `force-cuts when the video PID never resolves at all`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, maxSegmentDurationMillis = 8_000)
        // No PAT/PMT ever seen - videoPid stays null forever, so only the hard cap can fire.
        // Without PCR there's no elapsed-time signal either, so this exercises "never cuts" safely
        // rather than forcing a cut with no timing reference (asserted via absence of a crash/NPE).
        repeat(50) { assertNull(segmenter.feed(videoPacket(pusi = false, keyframe = false))) }
    }

    @Test
    fun `reads PCR from a separate PCR PID declared in the PMT, not the video PID`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket(pcrPid = PCR_ONLY_PID))
        // Video packets here never carry their own PCR - if PCR were (incorrectly) only ever read
        // from the video PID once it's known, elapsed time would never advance and this would never
        // cut, reproducing the OOM-by-unbounded-buffer bug this fix addresses.
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = null))
        segmenter.feed(pcrOnlyPacket(PCR_ONLY_PID, secondsToTicks(0.0)))
        segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = null))
        segmenter.feed(pcrOnlyPacket(PCR_ONLY_PID, secondsToTicks(3.0)))
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = null)))
        segmenter.feed(pcrOnlyPacket(PCR_ONLY_PID, secondsToTicks(4.5)))
        segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = null))
        segmenter.feed(pcrOnlyPacket(PCR_ONLY_PID, secondsToTicks(5.2)))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = null))
        assertTrue(completed != null)
    }

    @Test
    fun `keyframe-like bytes on a foreign PID never count as a keyframe boundary`() {
        val garbagePid = 0x105
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        // A packet on an unrelated PID (teletext/DVB subtitles in the field report this guards
        // against) with the exact same payload_unit_start + adaptation-field random_access_indicator
        // bit pattern as a genuine video keyframe packet - if keyframe detection ever inspected this
        // packet's content instead of filtering by videoPid first, this alone would wrongly count as
        // a keyframe boundary and could cut a segment years before the target duration.
        val garbageLooksLikeKeyframe = videoPacket(pusi = true, keyframe = true, pcrBase = null)
        garbageLooksLikeKeyframe[1] = (0x40 or ((garbagePid shr 8) and 0x1F)).toByte()
        garbageLooksLikeKeyframe[2] = (garbagePid and 0xFF).toByte()
        assertNull(segmenter.feed(garbageLooksLikeKeyframe))

        // The segmenter behaves exactly as if the garbage packet had never been fed: only a real
        // video-PID keyframe at or after the target duration actually cuts.
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(3.0))))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(5.2)))
        assertEquals(0, completed?.sequence)
    }

    @Test
    fun `never reads PCR from anywhere once the PMT declares PCR_PID none (0x1FFF)`() {
        val maxBytes = 2_000
        val segmenter = TsSegmenter(targetDurationMillis = 5_000, maxSegmentBytes = maxBytes)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket(pcrPid = PCR_PID_NONE))
        var cutCount = 0
        repeat(50) {
            // These video packets do embed a real PCR - a broadcaster bug this test also guards
            // against, since the PMT already declared this program carries no PCR at all and that
            // declaration must win regardless of what any individual packet claims.
            val completed = segmenter.feed(videoPacket(pusi = true, keyframe = false, pcrBase = secondsToTicks(0.0)))
            if (completed != null) {
                cutCount++
                assertTrue(completed.bytes.size <= maxBytes + 188)
            }
        }
        assertTrue(cutCount > 0)
    }

    @Test
    fun `force-cuts on the byte limit alone, with no PCR or keyframe signal at all`() {
        val maxBytes = 1_000
        val segmenter = TsSegmenter(maxSegmentBytes = maxBytes)
        var cutCount = 0
        repeat(20) {
            val completed = segmenter.feed(videoPacket(pusi = false, keyframe = false))
            if (completed != null) {
                cutCount++
                assertTrue(completed.bytes.size <= maxBytes + 188)
            }
        }
        assertTrue(cutCount > 0)
    }

    @Test
    fun `the first startupSegments segments cut at the shorter startup target`() {
        val segmenter = TsSegmenter(
            targetDurationMillis = 5_000,
            startupSegments = 2,
            startupTargetDurationMillis = 2_000,
        )
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        // 2.5s < the configured 5s target, but past the 2s startup target - segment 0 cuts here.
        val first = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(2.5)))
        assertEquals(0, first?.sequence)

        // Segment 1 is still within startupSegments (index 1 < 2), so it also uses the short target.
        val second = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(4.7)))
        assertEquals(1, second?.sequence)
    }

    @Test
    fun `segments at or after startupSegments use the configured target again`() {
        val segmenter = TsSegmenter(
            targetDurationMillis = 5_000,
            startupSegments = 1,
            startupTargetDurationMillis = 2_000,
        )
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        // Segment 0 uses the 2s startup target.
        val first = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(2.1)))
        assertEquals(0, first?.sequence)

        // Segment 1 is no longer within startupSegments (1) - 2.5s isn't enough to cut at the
        // normal 5s target, only the still-short startup one, so this must NOT cut yet.
        assertNull(segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(2.5))))
    }

    @Test
    fun `ramped startup segments still respect the byte safety valve`() {
        val maxBytes = 1_000
        val segmenter = TsSegmenter(
            maxSegmentBytes = maxBytes,
            startupSegments = 2,
            startupTargetDurationMillis = 2_000,
        )
        var cutCount = 0
        repeat(20) {
            val completed = segmenter.feed(videoPacket(pusi = false, keyframe = false))
            if (completed != null) {
                cutCount++
                assertTrue(completed.bytes.size <= maxBytes + 188)
            }
        }
        assertTrue(cutCount > 0)
    }

    @Test
    fun `flush returns null when nothing is buffered`() {
        val segmenter = TsSegmenter()
        assertNull(segmenter.flush())
    }

    @Test
    fun `onReconnect flushes the pre-gap buffer as a non-discontinuous segment`() {
        val segmenter = TsSegmenter(targetDurationMillis = 5_000)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        val flushed = segmenter.onReconnect()
        assertTrue(flushed != null)
        assertEquals(false, flushed?.discontinuity)
    }

    @Test
    fun `the first segment produced after a reconnect is marked as a discontinuity`() {
        val segmenter = TsSegmenter(targetDurationMillis = 1_000, startupSegments = 0)
        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        segmenter.onReconnect()

        // Same channel (PAT/PMT/videoPid untouched), PCR clock restarts from zero after the gap.
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(1.5)))
        assertEquals(true, completed?.discontinuity)

        // The discontinuity flag doesn't carry over to the segment after that one.
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(1.5)))
        val next = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(3.0)))
        assertEquals(false, next?.discontinuity)
    }

    @Test
    fun `onReconnect on an empty buffer returns null but still arms the discontinuity flag`() {
        val segmenter = TsSegmenter(targetDurationMillis = 1_000, startupSegments = 0)
        assertNull(segmenter.onReconnect())

        segmenter.feed(patPacket())
        segmenter.feed(pmtPacket())
        segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(0.0)))
        val completed = segmenter.feed(videoPacket(pusi = true, keyframe = true, pcrBase = secondsToTicks(1.5)))
        assertEquals(true, completed?.discontinuity)
    }

    @Test
    fun `non-sync-byte input is ignored`() {
        val segmenter = TsSegmenter()
        assertNull(segmenter.feed(ByteArray(188)))
        assertNull(segmenter.flush())
    }

    @Test
    fun `feed ignores a packet shorter than 188 bytes without throwing`() {
        val segmenter = TsSegmenter()
        assertNull(segmenter.feed(ByteArray(50)))
    }

    @Test
    fun `feed never throws on a PMT section truncated mid-header, at every size boundary`() {
        for (size in TRUNCATED_SECTION_SIZES) {
            val segmenter = TsSegmenter()
            assertNull(segmenter.feed(patPacket()))
            assertNull(segmenter.feed(truncatedPmtPacket(size)))
        }
    }

    @Test
    fun `wrapAwareDelta handles a normal forward delta`() {
        assertEquals(1_000L, wrapAwareDelta(500L, 1_500L))
    }

    @Test
    fun `wrapAwareDelta handles a wrap around the 33-bit PCR base`() {
        val modulus = 1L shl 33
        val start = modulus - 100
        val last = 50L
        assertEquals(150L, wrapAwareDelta(start, last))
    }
}
