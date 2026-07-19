package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastCompatibilityPolicyTest {

    @Test
    fun `H264 with AAC is compatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Aac))
        assertEquals(CastCompatibilityVerdict.Compatible, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `H264 with AAC-LATM is compatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.AacLatm))
        assertEquals(CastCompatibilityVerdict.Compatible, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `MP2-only audio is incompatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Mp2))
        assertEquals(CastCompatibilityVerdict.IncompatibleAudio(AudioCodec.Mp2), CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `AC3-only audio is incompatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Ac3))
        assertEquals(CastCompatibilityVerdict.IncompatibleAudio(AudioCodec.Ac3), CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `EAC3-only audio is incompatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Eac3))
        val expected = CastCompatibilityVerdict.IncompatibleAudio(AudioCodec.Eac3)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `at least one AAC track among several is still compatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Ac3, AudioCodec.Aac))
        assertEquals(CastCompatibilityVerdict.Compatible, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `HEVC video is incompatible regardless of audio`() {
        val info = TsProgramInfo(VideoCodec.Hevc, listOf(AudioCodec.Aac))
        val expected = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Hevc)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `MPEG-2 video is incompatible`() {
        val info = TsProgramInfo(VideoCodec.Mpeg2Video, listOf(AudioCodec.Aac))
        val expected = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `bad video wins over bad audio`() {
        val info = TsProgramInfo(VideoCodec.Hevc, listOf(AudioCodec.Ac3))
        assertEquals(CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Hevc), CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `null info is unknown`() {
        assertEquals(CastCompatibilityVerdict.Unknown, CastCompatibilityPolicy.classify(null))
    }

    @Test
    fun `no codecs detected at all is unknown`() {
        val info = TsProgramInfo(null, emptyList())
        assertEquals(CastCompatibilityVerdict.Unknown, CastCompatibilityPolicy.classify(info))
    }
}
