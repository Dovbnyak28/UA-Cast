package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

private fun buildStream(streams: List<Pair<Int, Int>>): ByteArray {
    val pat = tsPacket(0x0000, buildPatSection(PROGRAM_NUMBER, PMT_PID))
    val pmt = tsPacket(PMT_PID, buildPmtSection(PROGRAM_NUMBER, streams.first().second, streams))
    return pat + pmt
}

class TsProgramInfoParserTest {

    @Test
    fun `detects H264 video and AAC audio`() {
        val bytes = buildStream(listOf(0x1B to VIDEO_PID, 0x0F to AUDIO_PID))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(VideoCodec.H264, info?.videoCodec)
        assertEquals(listOf(AudioCodec.Aac), info?.audioCodecs)
    }

    @Test
    fun `detects H264 video and MP2 audio`() {
        val bytes = buildStream(listOf(0x1B to VIDEO_PID, 0x04 to AUDIO_PID))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(VideoCodec.H264, info?.videoCodec)
        assertEquals(listOf(AudioCodec.MpegAudio), info?.audioCodecs)
    }

    @Test
    fun `detects HEVC video and AC-3 audio`() {
        val bytes = buildStream(listOf(0x24 to VIDEO_PID, 0x81 to AUDIO_PID))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(VideoCodec.Hevc, info?.videoCodec)
        assertEquals(listOf(AudioCodec.Ac3), info?.audioCodecs)
    }

    @Test
    fun `collects every audio track, not just the first`() {
        val bytes = buildStream(listOf(0x1B to VIDEO_PID, 0x0F to AUDIO_PID, 0x81 to AUDIO_PID + 1))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(listOf(AudioCodec.Aac, AudioCodec.Ac3), info?.audioCodecs)
    }

    @Test
    fun `unrecognized but AV-range stream types map to Unknown with their code`() {
        val bytes = buildStream(listOf(0x20 to VIDEO_PID, 0x8A to AUDIO_PID))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(VideoCodec.Unknown(0x20), info?.videoCodec)
        assertEquals(listOf(AudioCodec.Unknown(0x8A)), info?.audioCodecs)
    }

    @Test
    fun `returns null for data with no sync byte`() {
        assertNull(TsProgramInfoParser.parse(ByteArray(500)))
    }

    @Test
    fun `returns null for a stream with no PAT`() {
        val pmtOnly = tsPacket(PMT_PID, buildPmtSection(PROGRAM_NUMBER, VIDEO_PID, listOf(0x1B to VIDEO_PID)))
        assertNull(TsProgramInfoParser.parse(pmtOnly))
    }

    @Test
    fun `returns null for too short input`() {
        assertNull(TsProgramInfoParser.parse(ByteArray(10)))
    }

    @Test
    fun `ignores unrelated PIDs when scanning for PAT and PMT`() {
        val noise = tsPacket(0x1FFF, ByteArray(180))
        val bytes = noise + buildStream(listOf(0x1B to VIDEO_PID, 0x0F to AUDIO_PID))
        val info = TsProgramInfoParser.parse(bytes)
        assertEquals(VideoCodec.H264, info?.videoCodec)
        assertTrue(info?.audioCodecs == listOf(AudioCodec.Aac))
    }
}
