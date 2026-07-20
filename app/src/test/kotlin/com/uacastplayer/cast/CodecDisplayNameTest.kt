package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CodecDisplayNameTest {

    @Test
    fun `names known video codecs`() {
        assertEquals("H.264", CodecDisplayName.of(VideoCodec.H264))
        assertEquals("HEVC", CodecDisplayName.of(VideoCodec.Hevc))
        assertEquals("MPEG-2", CodecDisplayName.of(VideoCodec.Mpeg2Video))
    }

    @Test
    fun `names known audio codecs`() {
        assertEquals("AAC", CodecDisplayName.of(AudioCodec.Aac))
        assertEquals("AAC", CodecDisplayName.of(AudioCodec.AacLatm))
        assertEquals("MP2", CodecDisplayName.of(AudioCodec.MpegAudio))
        assertEquals("AC-3", CodecDisplayName.of(AudioCodec.Ac3))
        assertEquals("E-AC-3", CodecDisplayName.of(AudioCodec.Eac3))
    }

    @Test
    fun `falls back to the raw PMT stream type for an unrecognized codec`() {
        assertEquals("video (type 5)", CodecDisplayName.of(VideoCodec.Unknown(5)))
        assertEquals("audio (type 9)", CodecDisplayName.of(AudioCodec.Unknown(9)))
    }
}
