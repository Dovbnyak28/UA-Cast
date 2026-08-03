package com.uacastplayer.cast

/**
 * Reduces receiver playback status changes, including the synthetic DISCONNECTED status used for
 * session loss. DISCONNECTED is where the handoff back to local playback happens: the local
 * player resumes, any proxy session is closed, and a channel switch requested while casting
 * (queued rather than applied immediately - see [requestChannelSwitch]) is finally applied.
 */
object CastReceiverStatusReducer {

    /** [selfInitiated] is true only for the first status update after this app itself issued a
     * load - a new `load()` naturally interrupts whatever the receiver was doing before, and the
     * Cast SDK reports that interruption as an ordinary IDLE (CANCELLED/INTERRUPTED/NONE), not an
     * error. Without this, that transient IDLE would be indistinguishable from the receiver
     * genuinely failing on its own, closing a proxy session and bouncing back to local playback
     * mid-switch. A self-initiated IDLE that *is* ERROR or FINISHED still goes through the normal
     * handling below - an actual error is still an error even if it happens to follow a load. */
    fun reduce(
        state: CastPlaybackState,
        status: ReceiverStatus,
        idleReason: IdleReason = IdleReason.NONE,
        selfInitiated: Boolean = false,
    ): CastReducerResult {
        if (status == ReceiverStatus.DISCONNECTED) return reduceDisconnected(state)
        return reduceConnected(state, status, idleReason, selfInitiated)
    }

    private fun reduceConnected(
        state: CastPlaybackState,
        status: ReceiverStatus,
        idleReason: IdleReason,
        selfInitiated: Boolean,
    ): CastReducerResult {
        var newState = state.copy(isSessionConnected = true, receiverStatus = status, idleReason = idleReason)
        if (isSelfInitiatedIdle(status, idleReason, selfInitiated)) return CastReducerResult(newState)

        val effects = mutableListOf<CastSideEffect>()
        when {
            status == ReceiverStatus.PLAYING -> {
                // PLAYING clears recoveringWithoutPlayback by definition - the premise it stands on
                // is that nothing has ever played.
                newState = newState.copy(
                    receiverLoadFailed = false,
                    isRecovering = false,
                    recoveringWithoutPlayback = false,
                )
                effects += CastSideEffect.PauseLocalPlayer
            }
            status == ReceiverStatus.IDLE && idleReason == IdleReason.ERROR -> {
                newState = newState.copy(receiverLoadFailed = true)
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

    private fun isSelfInitiatedIdle(status: ReceiverStatus, idleReason: IdleReason, selfInitiated: Boolean): Boolean {
        val isDefiniteOutcome = idleReason == IdleReason.ERROR || idleReason == IdleReason.FINISHED
        return status == ReceiverStatus.IDLE && selfInitiated && !isDefiniteOutcome
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
            deliveryMode = CastDeliveryMode.Direct,
            codecIncompatibility = null,
            receiverLoadFailed = false,
            likelyCompatibilityHint = null,
            isRecovering = false,
            recoveringWithoutPlayback = false,
            proxyUnavailableIpv4Only = false,
        )
        return CastReducerResult(newState, effects)
    }
}
