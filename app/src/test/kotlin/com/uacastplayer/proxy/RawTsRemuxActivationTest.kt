package com.uacastplayer.proxy

import com.uacastplayer.cast.AudioCodec
import com.uacastplayer.cast.CastCompatibilityVerdict
import com.uacastplayer.cast.VideoCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTsRemuxActivationTest {

    @Test
    fun `activates for a raw compatible TS stream when the feature is enabled`() {
        assertTrue(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.Compatible,
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `does not activate for an actual HLS playlist`() {
        assertFalse(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = true,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.Compatible,
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `does not activate for bytes that don't look like MPEG-TS`() {
        assertFalse(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = false,
                verdict = CastCompatibilityVerdict.Compatible,
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `does not activate for an incompatible codec - remuxing the container would not help`() {
        assertFalse(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Hevc),
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `activates for an unknown verdict - a raw TS passthrough is never playable either way`() {
        assertTrue(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.Unknown,
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `does not activate for an incompatible audio codec`() {
        assertFalse(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.IncompatibleAudio(AudioCodec.Mp2),
                featureEnabled = true,
            ),
        )
    }

    @Test
    fun `does not activate when the feature flag is off`() {
        assertFalse(
            RawTsRemuxActivation.shouldActivate(
                isHlsPlaylist = false,
                looksLikeMpegTs = true,
                verdict = CastCompatibilityVerdict.Compatible,
                featureEnabled = false,
            ),
        )
    }
}
