package com.uacastplayer.player

import com.uacastplayer.core.cast.AudioCodec
import com.uacastplayer.core.cast.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** The small Chromecast-facing contract the local player needs; the Cast SDK adapter stays out of
 * the player feature and is connected by the app composition root. */
interface PlayerCastPort {
    val state: Flow<PlayerCastState>
    val sideEffects: Flow<PlayerCastSideEffect>

    fun setActiveChannel(channel: PlayerCastChannel)
}

/** Implemented by the Application so an Activity-scoped [PlayerViewModel] can obtain the port
 * without importing the app composition root or the Cast SDK adapter. */
interface PlayerCastPortOwner {
    val playerCastPort: PlayerCastPort
}

data class PlayerCastState(
    val isConnected: Boolean = false,
    val statusMessage: PlayerCastStatusMessage? = null,
)

data class PlayerCastChannel(
    val index: Int,
    val streamUrl: String,
    val title: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val logoUrl: String? = null,
)

sealed interface PlayerCastSideEffect {
    data object PauseLocalPlayer : PlayerCastSideEffect
    data object ResumeLocalPlayer : PlayerCastSideEffect
    data class RecordIncompatibility(val reason: String) : PlayerCastSideEffect
    data object CloseProxySession : PlayerCastSideEffect
    data class ApplyPendingChannelSwitch(val index: Int) : PlayerCastSideEffect
}

sealed interface PlayerCastStatusMessage {
    data class IncompatibleVideo(val codec: VideoCodec) : PlayerCastStatusMessage
    data object ProxyUnavailableIpv4Only : PlayerCastStatusMessage
    data object Recovering : PlayerCastStatusMessage
    data class LikelyIncompatibleVideo(val codec: VideoCodec) : PlayerCastStatusMessage
    data class LikelyIncompatibleAudio(val codec: AudioCodec) : PlayerCastStatusMessage
    data object ReceiverLoadFailed : PlayerCastStatusMessage
}

/** Safe fallback for isolated tests or a host Application that has not installed Cast support. */
internal object DisconnectedPlayerCastPort : PlayerCastPort {
    override val state: Flow<PlayerCastState> = flowOf(PlayerCastState())
    override val sideEffects: Flow<PlayerCastSideEffect> = emptyFlow()

    override fun setActiveChannel(channel: PlayerCastChannel) = Unit
}
