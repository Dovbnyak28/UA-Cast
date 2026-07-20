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
    fun `MP2-only audio is LikelyCompatible, not blocked`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.MpegAudio))
        val expected = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.MpegAudio, videoHint = null)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `AC3-only audio is LikelyCompatible, not blocked`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Ac3))
        val expected = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.Ac3, videoHint = null)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `EAC3-only audio is LikelyCompatible, not blocked`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Eac3))
        val expected = CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.Eac3, videoHint = null)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `at least one AAC track among several is still compatible`() {
        val info = TsProgramInfo(VideoCodec.H264, listOf(AudioCodec.Ac3, AudioCodec.Aac))
        assertEquals(CastCompatibilityVerdict.Compatible, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `HEVC video with AAC audio is LikelyCompatible, not blocked`() {
        val info = TsProgramInfo(VideoCodec.Hevc, listOf(AudioCodec.Aac))
        val expected = CastCompatibilityVerdict.LikelyCompatible(audioHint = null, videoHint = VideoCodec.Hevc)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `MPEG-2 video is the one hard incompatibility`() {
        val info = TsProgramInfo(VideoCodec.Mpeg2Video, listOf(AudioCodec.Aac))
        val expected = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `MPEG-2 video blocks even with a compatible audio track`() {
        val info = TsProgramInfo(VideoCodec.Mpeg2Video, listOf(AudioCodec.Ac3))
        val expected = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
    }

    @Test
    fun `HEVC video and AC3 audio both surface as hints on the same LikelyCompatible verdict`() {
        val info = TsProgramInfo(VideoCodec.Hevc, listOf(AudioCodec.Ac3))
        val expected =
            CastCompatibilityVerdict.LikelyCompatible(audioHint = AudioCodec.Ac3, videoHint = VideoCodec.Hevc)
        assertEquals(expected, CastCompatibilityPolicy.classify(info))
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
