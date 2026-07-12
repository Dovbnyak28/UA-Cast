package com.uacastplayer.cast

enum class CastLoadPhase { IDLE, LOADING, LOADED, FAILED }

/** Mirrors the receiver's actual playback state, plus a synthetic DISCONNECTED for session loss. */
enum class ReceiverStatus { BUFFERING, PLAYING, PAUSED, IDLE, DISCONNECTED }

enum class IdleReason { NONE, FINISHED, ERROR, CANCELLED, INTERRUPTED }

data class CastPlaybackState(
    val isSessionConnected: Boolean = false,
    val loadPhase: CastLoadPhase = CastLoadPhase.IDLE,
    val receiverStatus: ReceiverStatus = ReceiverStatus.DISCONNECTED,
    val idleReason: IdleReason = IdleReason.NONE,
    val pendingChannelIndex: Int? = null,
)

sealed class CastLoadResult {
    data object Success : CastLoadResult()
    data class Failure(val reason: String) : CastLoadResult()
}

/**
 * Signals for the caller to act on; the reducers themselves never touch the player, disk, or
 * network directly.
 */
sealed class CastSideEffect {
    data object PauseLocalPlayer : CastSideEffect()
    data object ResumeLocalPlayer : CastSideEffect()
    data class RecordIncompatibility(val reason: String) : CastSideEffect()
    data object CloseProxySession : CastSideEffect()
    data class ApplyPendingChannelSwitch(val index: Int) : CastSideEffect()
}

data class CastReducerResult(val state: CastPlaybackState, val effects: List<CastSideEffect> = emptyList())
