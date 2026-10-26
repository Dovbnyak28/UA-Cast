package com.uacastplayer.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEventListenerTest {
    @Test fun completionIsReportedOnlyForTheEndedState() {
        val state = MutableStateFlow(PlayerUiState())
        var completions = 0
        val listener = PlayerEventListener(state, PlayerEventHooks({}, {}, {}, {}, {}, { completions++ })) {
            PlayerTrackSnapshot(PlaybackBadgesState(), emptyList(), emptyList())
        }
        for (phase in listOf(Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY)) {
            listener.onPlaybackStateChanged(phase)
        }
        assertEquals(0, completions)
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(1, completions)
        assertFalse(state.value.isBuffering)
    }

    @Test fun adapterPreservesPlayingPauseBufferingAndVideoMetadataUpdates() {
        val state = MutableStateFlow(PlayerUiState())
        val events = mutableListOf<String>()
        val listener = PlayerEventListener(state, PlayerEventHooks(
            { events += "confirmed" }, { events += "pause" }, { events += "controls" },
            { events += "seekability" }, { events += "error" },
        )) {
            events += "tracks"
            PlayerTrackSnapshot(PlaybackBadgesState(), emptyList(), emptyList())
        }
        listener.onIsPlayingChanged(true)
        assertTrue(state.value.isPlaying)
        assertEquals(listOf("confirmed", "controls"), events)
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertTrue(state.value.isBuffering)
        assertTrue(events.contains("pause"))
        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertFalse(state.value.isBuffering)
        val size = VideoSize(640, 360)
        listener.onVideoSizeChanged(size)
        assertEquals(size, state.value.videoSize)
        listener.onTracksChanged(Tracks.EMPTY)
        listener.onPlayerError(PlaybackException("synthetic", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(events.contains("tracks"))
        assertTrue(events.contains("error"))
    }
}
