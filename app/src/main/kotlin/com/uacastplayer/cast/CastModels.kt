package com.uacastplayer.cast

enum class CastLoadPhase { IDLE, LOADING, LOADED, FAILED }

/** Mirrors the receiver's actual playback state, plus a synthetic DISCONNECTED for session loss. */
enum class ReceiverStatus { BUFFERING, PLAYING, PAUSED, IDLE, DISCONNECTED }

enum class IdleReason { NONE, FINISHED, ERROR, CANCELLED, INTERRUPTED }

/** Surfaced when [CastCompatibilityPolicy] finds a codec that hard-blocks casting (MPEG-2 video
 * only, see [CastCompatibilityVerdict.IncompatibleVideo]) - proxy fallback would not help (it
 * re-serves the same codecs, see docs/PROXY_RULES.md), so this is shown to the user - naming the
 * actual codec (see [CodecDisplayName]) rather than a vague "not supported" - instead of silently
 * retrying. Cleared whenever a new channel starts casting. */
sealed class CodecIncompatibility {
    data class Video(val codec: VideoCodec) : CodecIncompatibility()
}

data class CastPlaybackState(
    val isSessionConnected: Boolean = false,
    val loadPhase: CastLoadPhase = CastLoadPhase.IDLE,
    val receiverStatus: ReceiverStatus = ReceiverStatus.DISCONNECTED,
    val idleReason: IdleReason = IdleReason.NONE,
    val pendingChannelIndex: Int? = null,
    val deliveryMode: CastDeliveryMode = CastDeliveryMode.Direct,
    val codecIncompatibility: CodecIncompatibility? = null,
    // The receiver went idle/error even after falling back to the proxy - unlike
    // codecIncompatibility, this covers everything else that can go wrong (the proxy URL
    // unreachable from the receiver's network, a malformed playlist, etc.), where retrying
    // wouldn't obviously help either. Previously this only logged a debug line - from the user's
    // side, casting silently reverting to local playback with no explanation looked identical to
    // "casting doesn't work at all".
    val receiverLoadFailed: Boolean = false,
    // A non-blocking codec hint from CastCompatibilityPolicy.LikelyCompatible - never prevents a
    // cast attempt, only picked up if receiverLoadFailed ends up true, so the failure message can
    // name a likely cause instead of a generic "couldn't cast" with no explanation.
    val likelyCompatibilityHint: CastCompatibilityVerdict.LikelyCompatible? = null,
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
