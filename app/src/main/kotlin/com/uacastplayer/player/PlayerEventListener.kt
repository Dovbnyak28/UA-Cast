package com.uacastplayer.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class PlayerEventHooks(
    val playbackConfirmed: () -> Unit,
    val playbackPaused: () -> Unit,
    val controlsChanged: () -> Unit,
    val seekabilityChanged: () -> Unit,
    val error: (PlaybackException) -> Unit,
    val playbackEnded: () -> Unit = {},
)

/** Media3-to-UI event wiring only. Session/retry policy and resource lifetime stay in the owner. */
internal class PlayerEventListener(
    private val state: MutableStateFlow<PlayerUiState>, private val hooks: PlayerEventHooks,
    private val mapTracks: (Tracks) -> PlayerTrackSnapshot,
) : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) { hooks.controlsChanged() }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) hooks.playbackConfirmed()
        state.update { it.copy(isPlaying = isPlaying, autoSkipRecovery = if (isPlaying) null else it.autoSkipRecovery) }
        hooks.controlsChanged()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) hooks.playbackPaused()
        hooks.controlsChanged()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        if (playbackState == Player.STATE_ENDED) hooks.playbackEnded()
        hooks.seekabilityChanged()
    }

    override fun onPlayerError(error: PlaybackException) { hooks.error(error) }
    override fun onTracksChanged(tracks: Tracks) {
        val snapshot = mapTracks(tracks)
        state.update {
            it.copy(badges = snapshot.badges, audioTracks = snapshot.audioTracks, textTracks = snapshot.textTracks)
        }
    }
    override fun onVideoSizeChanged(videoSize: VideoSize) { state.update { it.copy(videoSize = videoSize) } }
}
