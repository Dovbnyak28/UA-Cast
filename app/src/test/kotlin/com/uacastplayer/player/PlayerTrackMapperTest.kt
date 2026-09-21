package com.uacastplayer.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTrackMapperTest {

    private val mapper = PlayerTrackMapper { index -> "Unknown $index" }

    @Test
    fun `maps selected Media3 formats to badges and picker tracks`() {
        val video = group(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1920)
                .setHeight(1080)
                .setFrameRate(50f)
                .build(),
            selected = true,
        )
        val audio = group(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setLanguage("uk")
                .setChannelCount(6)
                .setSampleRate(48_000)
                .build(),
            selected = true,
        )
        val text = group(
            Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en").build(),
            selected = false,
        )

        val snapshot = mapper.map(Tracks(listOf(video, audio, text)))

        assertEquals("1080p", snapshot.badges.qualityLabel)
        assertEquals("H.264", snapshot.badges.videoCodecLabel)
        assertEquals("AAC", snapshot.badges.audioCodecLabel)
        assertEquals(AudioChannelLayout.SURROUND_5_1, snapshot.badges.channelLayout)
        assertEquals(1, snapshot.audioTracks.size)
        assertTrue(snapshot.audioTracks.single().isSelected)
        assertEquals(1, snapshot.textTracks.size)
        assertFalse(snapshot.textTracks.single().isSelected)
    }

    @Test
    fun `uses injected unknown label when Media3 supplies no label or language`() {
        val audio = group(
            Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(),
            selected = false,
        )

        val snapshot = mapper.map(Tracks(listOf(audio)))

        assertEquals("Unknown 1", snapshot.audioTracks.single().label)
    }

    private fun group(format: Format, selected: Boolean): Tracks.Group = Tracks.Group(
        TrackGroup(format),
        false,
        intArrayOf(C.FORMAT_HANDLED),
        booleanArrayOf(selected),
    )
}
