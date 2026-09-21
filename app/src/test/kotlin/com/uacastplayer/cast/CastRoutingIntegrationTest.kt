package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastCompatibilityPolicy
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsProgramInfoParser
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.data.cast.TsSourceClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PROGRAM_NUMBER = 1
private const val PMT_PID = 0x100
private const val VIDEO_PID = 0x101
private const val AUDIO_PID = 0x102

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
    val programInfoLength = 0
    val sectionLength = 2 + 1 + 1 + 1 + 2 + 2 + programInfoLength + streamsBytes.size + 4
    val header = byteArrayOf(
        0x02,
        (0xB0 or ((sectionLength shr 8) and 0x0F)).toByte(), (sectionLength and 0xFF).toByte(),
        (programNumber shr 8).toByte(), (programNumber and 0xFF).toByte(),
        0xC1.toByte(),
        0x00,
        0x00,
        (0xE0 or ((pcrPid shr 8) and 0x1F)).toByte(), (pcrPid and 0xFF).toByte(),
        (0xF0 or ((programInfoLength shr 8) and 0x0F)).toByte(), (programInfoLength and 0xFF).toByte(),
    )
    return header + streamsBytes + ByteArray(4)
}

/** A minimal, realistic raw-TS byte prefix (PAT + PMT declaring the given elementary streams) -
 * exactly what [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic] would have probed from a raw
 * MPEG-TS origin, with no HLS playlist wrapping at all. */
private fun rawTsBytes(streams: List<Pair<Int, Int>>): ByteArray {
    val pat = tsPacket(0x0000, buildPatSection(PROGRAM_NUMBER, PMT_PID))
    val pmt = tsPacket(PMT_PID, buildPmtSection(PROGRAM_NUMBER, streams.first().second, streams))
    return pat + pmt
}

/**
 * Exercises the pure classify -> parse -> verdict -> route chain end to end, without any network
 * I/O (see docs/CAST_PLAYBACK_RULES.md's routing table) - the three cases this iteration exists
 * for. [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic]'s own HTTP glue is deliberately not
 * covered here; there's no MockWebServer in this project, and every interesting decision it makes
 * is already pure and tested via [TsSourceClassifier] and this chain.
 */
class CastRoutingIntegrationTest {

    private fun routeFor(prefixBytes: ByteArray, contentType: String? = null): CastRouteDecision {
        val sourceKind = TsSourceClassifier.classify(contentType, prefixBytes)
        val programInfo = TsProgramInfoParser.parse(prefixBytes)
        val verdict = CastCompatibilityPolicy.classify(programInfo)
        return CastDeliveryStrategy.onDiagnosticResult(verdict, sourceKind)
    }

    @Test
    fun `raw TS with H264 and AAC routes straight to proxy remux`() {
        val bytes = rawTsBytes(listOf(0x1B to VIDEO_PID, 0x0F to AUDIO_PID))
        assertEquals(CastRouteDecision.ProxyImmediately, routeFor(bytes))
    }

    @Test
    fun `raw TS with MPEG-2 and MP2 is blocked with no proxy attempt`() {
        val bytes = rawTsBytes(listOf(0x02 to VIDEO_PID, 0x04 to AUDIO_PID))
        val expected = CastRouteDecision.Blocked(CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video))
        assertEquals(expected, routeFor(bytes))
    }

    @Test
    fun `HLS with H264 and AAC takes no route action - direct-then-watchdog proceeds as normal`() {
        // Mirrors the real two-request shape: the playlist bytes alone classify the source kind
        // (no PAT/PMT live in playlist text), and the segment bytes - fetched separately, per
        // TsFirstSegmentDiagnostic.diagnoseHlsSegment - carry the actual codec info.
        val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6.0,\nsegment0.ts\n".toByteArray()
        val sourceKind = TsSourceClassifier.classify(contentType = null, prefixBytes = playlist)
        assertEquals(TsSourceKind.Hls, sourceKind)

        val segmentBytes = rawTsBytes(listOf(0x1B to VIDEO_PID, 0x0F to AUDIO_PID))
        val verdict = CastCompatibilityPolicy.classify(TsProgramInfoParser.parse(segmentBytes))
        assertEquals(CastCompatibilityVerdict.Compatible, verdict)

        assertEquals(CastRouteDecision.NoAction, CastDeliveryStrategy.onDiagnosticResult(verdict, sourceKind))
    }
}
