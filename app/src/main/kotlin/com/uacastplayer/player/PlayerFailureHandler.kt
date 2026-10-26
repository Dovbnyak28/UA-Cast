package com.uacastplayer.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.uacastplayer.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class PlayerFailureFlags(
    val asleep: Boolean, val remote: Boolean, val autoSkip: Boolean, val wrapAround: Boolean,
)

internal data class PlayerFailureContext(
    val player: Player,
    val session: PlayerSessionStateMachine,
    val retries: PlayerRetryScheduler,
    val state: MutableStateFlow<PlayerUiState>,
    val flags: () -> PlayerFailureFlags,
    val hasNetwork: () -> Boolean,
    val retry: () -> Unit,
    val cancelStall: () -> Unit,
    val switch: (PlayerSessionStateMachine.ChannelSwitch, AutoSkipRecoveryState) -> Unit,
    val controlsChanged: () -> Unit,
)

/** Error policy execution, distinct from Media3 event wiring and the ViewModel's lifecycle. */
internal class PlayerFailureHandler(private val owner: PlayerFailureContext) {
    fun handle(error: PlaybackException) {
        val flags = owner.flags()
        // Media3 can deliver an already queued error after the remote target took ownership.
        if (flags.asleep || flags.remote) return
        if (owner.state.value.currentChannel?.localVideo != null) {
            owner.retries.cancel()
            owner.cancelStall()
            fail(localVideo = true)
        } else handleStream(error, flags)
    }

    private fun handleStream(error: PlaybackException, flags: PlayerFailureFlags) {
        val fallback = MediaItemFactory.progressiveFallback(owner.player.currentMediaItem, error.errorCode)
        if (fallback != null && !flags.remote) {
            owner.player.setMediaItem(fallback)
            owner.retry()
        } else {
            val effect = owner.session.onPlaybackError(PlayerErrorClassifier.classify(error),
                System.currentTimeMillis(), owner.hasNetwork(), flags.autoSkip, flags.wrapAround)
            apply(effect)
        }
    }

    private fun apply(effect: PlayerSessionStateMachine.PlaybackFailureEffect) {
        when (effect) {
            is PlayerSessionStateMachine.PlaybackFailureEffect.RecoverLiveWindow -> {
                AppLog.d("PlayerViewModel") {
                    "Recovering from BehindLiveWindowException (attempt ${effect.attemptInWindow} in the last 60s)"
                }
                owner.player.seekToDefaultPosition()
                owner.retry()
            }
            is PlayerSessionStateMachine.PlaybackFailureEffect.Retry -> owner.retries.after(effect.delayMillis)
            PlayerSessionStateMachine.PlaybackFailureEffect.RetryWhenNetworkAvailable -> {
                AppLog.d("PlayerViewModel") { "No network: retrying the same channel instead of marking it dead" }
                owner.retries.whenNetworkAvailable()
            }
            is PlayerSessionStateMachine.PlaybackFailureEffect.SwitchChannel -> owner.switch(effect.transition,
                AutoSkipRecoveryState(effect.skippedChannels, effect.totalChannels))
            PlayerSessionStateMachine.PlaybackFailureEffect.Fatal -> fail(localVideo = false)
        }
    }

    private fun fail(localVideo: Boolean) {
        owner.state.update { it.copy(fatalError = true, isBuffering = false, autoSkipRecovery = null,
            isRecoveringPlayback = if (localVideo) false else it.isRecoveringPlayback) }
        owner.controlsChanged()
    }
}
