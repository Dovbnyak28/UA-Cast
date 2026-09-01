package com.uacastplayer.app

import android.content.Context
import com.uacastplayer.cast.CastChannel
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.cast.CastStatusMessage
import com.uacastplayer.cast.CastStatusMessagePolicy
import com.uacastplayer.player.PlayerCastChannel
import com.uacastplayer.player.PlayerCastPort
import com.uacastplayer.player.PlayerCastSideEffect
import com.uacastplayer.player.PlayerCastState
import com.uacastplayer.player.PlayerCastStatusMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Composition-root adapter between the player-owned port and the Cast SDK repository. */
internal class PlayerCastAdapter(context: Context) : PlayerCastPort {
    private val repository = CastSessionRepository.getInstance(context)

    override val state: Flow<PlayerCastState> = repository.state.map { state ->
        PlayerCastState(
            isConnected = state.isSessionConnected,
            statusMessage = PlayerCastMapping.status(CastStatusMessagePolicy.messageFor(state)),
        )
    }

    override val sideEffects: Flow<PlayerCastSideEffect> = repository.sideEffects.map(PlayerCastMapping::effect)

    override fun setActiveChannel(channel: PlayerCastChannel) {
        repository.setActiveChannel(PlayerCastMapping.channel(channel))
    }
}

internal object PlayerCastMapping {
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
