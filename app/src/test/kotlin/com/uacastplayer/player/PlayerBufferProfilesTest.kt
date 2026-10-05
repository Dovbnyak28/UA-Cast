package com.uacastplayer.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import com.uacastplayer.core.settings.BufferSize
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class PlayerBufferProfilesTest {

    @Test
    fun `small profile keeps the lowest startup and memory budget`() {
        assertEquals(
            PlayerBufferProfile(
                minBufferMs = 10_000,
                maxBufferMs = 20_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
                targetBufferBytes = 8 * 1024 * 1024,
            ),
            PlayerBufferProfiles.forSize(BufferSize.SMALL),
        )
    }

    @Test
    fun `medium profile preserves Media3 cruise defaults with bounded memory`() {
        assertEquals(
            PlayerBufferProfile(
                minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_500,
                targetBufferBytes = 16 * 1024 * 1024,
            ),
            PlayerBufferProfiles.forSize(BufferSize.MEDIUM),
        )
    }

    @Test
    fun `large profile caps live cruise buffer and memory`() {
        assertEquals(
            PlayerBufferProfile(
                minBufferMs = 30_000,
                maxBufferMs = 35_000,
                bufferForPlaybackMs = 2_000,
                bufferForPlaybackAfterRebufferMs = 5_000,
                targetBufferBytes = 24 * 1024 * 1024,
            ),
            PlayerBufferProfiles.forSize(BufferSize.LARGE),
        )
    }
}
