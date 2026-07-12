package com.uacastplayer.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TsCompatibilityPolicyTest {

    @Test
    fun `MPEG-2 video is known unsupported`() {
        val info = TsStreamInfo(videoCodec = TsCodec.MPEG2_VIDEO, audioCodec = TsCodec.AAC)
        assertTrue(TsCompatibilityPolicy.isKnownUnsupported(info))
    }

    @Test
    fun `MP2 audio is known unsupported`() {
        val info = TsStreamInfo(videoCodec = TsCodec.H264, audioCodec = TsCodec.MPEG_AUDIO)
        assertTrue(TsCompatibilityPolicy.isKnownUnsupported(info))
    }

    @Test
    fun `H264 plus AAC is not known unsupported`() {
        val info = TsStreamInfo(videoCodec = TsCodec.H264, audioCodec = TsCodec.AAC)
        assertFalse(TsCompatibilityPolicy.isKnownUnsupported(info))
    }

    @Test
    fun `unknown codecs are not treated as known unsupported`() {
        val info = TsStreamInfo(videoCodec = TsCodec.UNKNOWN, audioCodec = TsCodec.UNKNOWN)
        assertFalse(TsCompatibilityPolicy.isKnownUnsupported(info))
    }

    @Test
    fun `null codecs are not treated as known unsupported`() {
        assertFalse(TsCompatibilityPolicy.isKnownUnsupported(TsStreamInfo(null, null)))
    }
}
