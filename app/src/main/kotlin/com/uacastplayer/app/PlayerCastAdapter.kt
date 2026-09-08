package com.uacastplayer.app

import android.content.Context
import com.uacastplayer.cast.CastChannel
import com.uacastplayer.cast.CastLoadPhase
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.cast.CastStatusMessage
import com.uacastplayer.cast.CastStatusMessagePolicy
import com.uacastplayer.player.PlayerCastChannel
import com.uacastplayer.player.PlayerCastPort
import com.uacastplayer.player.PlayerCastSideEffect
import com.uacastplayer.player.PlayerCastState
import com.uacastplayer.player.PlayerCastStatusMessage
import com.uacastplayer.player.PlaybackActivity
import com.uacastplayer.dlna.DlnaSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Composition-root adapter between the player-owned port and the Cast SDK repository. */
internal class PlayerCastAdapter(context: Context) : PlayerCastPort {
    private val repository = CastSessionRepository.getInstance(context)
    // This adapter is lazy-owned by Application. Remote state must continue publishing after the
    // Activity-scoped player is cleared; neither this scope nor the flows retain an Activity.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        PlaybackActivity.observeRemote(
            applicationScope,
            combine(repository.state, DlnaSessionRepository.getInstance(context).state) { cast, dlna ->
                PlayerCastMapping.state(cast).isConnected || dlna.isConnecting || dlna.connectedDevice != null
            },
        )
    }

    override val state: Flow<PlayerCastState> = repository.state.map(PlayerCastMapping::state)

    override val sideEffects: Flow<PlayerCastSideEffect> = repository.sideEffects.map(PlayerCastMapping::effect)

    override fun setActiveChannel(channel: PlayerCastChannel) {
        repository.setActiveChannel(PlayerCastMapping.channel(channel))
    }

    override fun stopPlayback() = repository.endSession()
}

internal object PlayerCastMapping {
    fun state(state: CastPlaybackState): PlayerCastState = PlayerCastState(
        // A connected SDK session can have a failed media load. That session no longer owns
        // playback, including after Activity recreation when no one-shot resume event is replayed.
        isConnected = state.loadPhase != CastLoadPhase.FAILED && !state.receiverLoadFailed &&
            (state.isSessionConnected || state.loadPhase == CastLoadPhase.LOADING),
        statusMessage = status(CastStatusMessagePolicy.messageFor(state)),
    )

    fun channel(channel: PlayerCastChannel): CastChannel = CastChannel(
        index = channel.index,
        streamUrl = channel.streamUrl,
        title = channel.title,
        userAgent = channel.userAgent,
        referrer = channel.referrer,
        logoUrl = channel.logoUrl,
    )

    fun effect(effect: CastSideEffect): PlayerCastSideEffect = when (effect) {
        CastSideEffect.PauseLocalPlayer -> PlayerCastSideEffect.PauseLocalPlayer
        CastSideEffect.ResumeLocalPlayer -> PlayerCastSideEffect.ResumeLocalPlayer
        is CastSideEffect.RecordIncompatibility -> PlayerCastSideEffect.RecordIncompatibility(effect.reason)
        CastSideEffect.CloseProxySession -> PlayerCastSideEffect.CloseProxySession
        is CastSideEffect.ApplyPendingChannelSwitch -> PlayerCastSideEffect.ApplyPendingChannelSwitch(effect.index)
    }

    fun status(message: CastStatusMessage?): PlayerCastStatusMessage? = when (message) {
        null -> null
        is CastStatusMessage.IncompatibleVideo -> PlayerCastStatusMessage.IncompatibleVideo(message.codec)
        CastStatusMessage.ProxyUnavailableIpv4Only -> PlayerCastStatusMessage.ProxyUnavailableIpv4Only
        CastStatusMessage.Recovering -> PlayerCastStatusMessage.Recovering
        is CastStatusMessage.LikelyIncompatibleVideo -> PlayerCastStatusMessage.LikelyIncompatibleVideo(message.codec)
        is CastStatusMessage.LikelyIncompatibleAudio -> PlayerCastStatusMessage.LikelyIncompatibleAudio(message.codec)
        CastStatusMessage.ReceiverLoadFailed -> PlayerCastStatusMessage.ReceiverLoadFailed
    }
}
