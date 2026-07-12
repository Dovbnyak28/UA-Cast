package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackBadgesTest {

    @Test
    fun `quality label maps common heights`() {
        assertEquals("4K", PlaybackBadges.qualityLabel(2160))
        assertEquals("1080p", PlaybackBadges.qualityLabel(1080))
        assertEquals("720p", PlaybackBadges.qualityLabel(720))
        assertEquals("480p", PlaybackBadges.qualityLabel(480))
    }

    @Test
    fun `quality label is null for a non-positive height`() {
        assertNull(PlaybackBadges.qualityLabel(0))
        assertNull(PlaybackBadges.qualityLabel(-1))
    }

    @Test
    fun `video codec labels map known mime types`() {
        assertEquals("H.264", PlaybackBadges.videoCodecLabel("video/avc"))
        assertEquals("H.265", PlaybackBadges.videoCodecLabel("video/hevc"))
        assertEquals("MPEG-2", PlaybackBadges.videoCodecLabel("video/mpeg2"))
    }

    @Test
    fun `video codec label is null for unknown mime type`() {
        assertNull(PlaybackBadges.videoCodecLabel("video/unknown"))
        assertNull(PlaybackBadges.videoCodecLabel(null))
    }

    @Test
    fun `audio codec labels map known mime types including IPTV-relevant ones`() {
        assertEquals("AAC", PlaybackBadges.audioCodecLabel("audio/mp4a-latm"))
        assertEquals("MP2", PlaybackBadges.audioCodecLabel("audio/mpeg-l2"))
        assertEquals("AC-3", PlaybackBadges.audioCodecLabel("audio/ac3"))
        assertEquals("DTS", PlaybackBadges.audioCodecLabel("audio/vnd.dts"))
    }

    @Test
    fun `channel layout maps common channel counts`() {
        assertEquals(AudioChannelLayout.MONO, PlaybackBadges.channelLayout(1))
        assertEquals(AudioChannelLayout.STEREO, PlaybackBadges.channelLayout(2))
        assertEquals(AudioChannelLayout.SURROUND_5_1, PlaybackBadges.channelLayout(6))
        assertEquals(AudioChannelLayout.SURROUND_7_1, PlaybackBadges.channelLayout(8))
    }

    @Test
    fun `channel layout falls back to OTHER for unusual counts`() {
        assertEquals(AudioChannelLayout.OTHER, PlaybackBadges.channelLayout(3))
    }
}
