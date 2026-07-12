package com.uacastplayer.cast

/**
 * Reduces receiver playback status changes, including the synthetic DISCONNECTED status used for
 * session loss. DISCONNECTED is where the handoff back to local playback happens: the local
 * player resumes, any proxy session is closed, and a channel switch requested while casting
 * (queued rather than applied immediately - see [requestChannelSwitch]) is finally applied.
 */
object CastReceiverStatusReducer {

    fun reduce(
        state: CastPlaybackState,
        status: ReceiverStatus,
        idleReason: IdleReason = IdleReason.NONE,
    ): CastReducerResult {
        if (status == ReceiverStatus.DISCONNECTED) return reduceDisconnected(state)

        val newState = state.copy(isSessionConnected = true, receiverStatus = status, idleReason = idleReason)
        val effects = mutableListOf<CastSideEffect>()

        when {
            status == ReceiverStatus.PLAYING -> effects += CastSideEffect.PauseLocalPlayer
            status == ReceiverStatus.IDLE && idleReason == IdleReason.ERROR -> {
                effects += CastSideEffect.RecordIncompatibility("receiver_idle_error")
                effects += CastSideEffect.CloseProxySession
                effects += CastSideEffect.ResumeLocalPlayer
            }
            status == ReceiverStatus.IDLE && idleReason == IdleReason.FINISHED -> {
                effects += CastSideEffect.CloseProxySession
            }
        }

        return CastReducerResult(newState, effects)
    }

    /** A channel switch requested mid-cast is queued, not applied - it fires on the next DISCONNECTED. */
    fun requestChannelSwitch(state: CastPlaybackState, index: Int): CastPlaybackState =
        state.copy(pendingChannelIndex = index)

    private fun reduceDisconnected(state: CastPlaybackState): CastReducerResult {
        val effects = mutableListOf<CastSideEffect>(
            CastSideEffect.ResumeLocalPlayer,
            CastSideEffect.CloseProxySession,
        )
        state.pendingChannelIndex?.let { effects += CastSideEffect.ApplyPendingChannelSwitch(it) }

        val newState = state.copy(
            isSessionConnected = false,
            receiverStatus = ReceiverStatus.DISCONNECTED,
            loadPhase = CastLoadPhase.IDLE,
            idleReason = IdleReason.NONE,
            pendingChannelIndex = null,
        )
        return CastReducerResult(newState, effects)
    }
}
