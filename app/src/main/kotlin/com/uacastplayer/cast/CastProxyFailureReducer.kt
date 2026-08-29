package com.uacastplayer.cast

/** State/effects for a local proxy that could not be prepared before any receiver load was issued. */
internal object CastProxyFailureReducer {

    fun reduce(state: CastPlaybackState): CastReducerResult = CastReducerResult(
        state = state.copy(
            loadPhase = CastLoadPhase.FAILED,
            deliveryMode = CastDeliveryMode.Direct,
            receiverLoadFailed = true,
            isRecovering = false,
            recoveringWithoutPlayback = false,
        ),
        // This is phone infrastructure failure, not evidence that the stream/receiver pair is
        // incompatible. In particular, do not emit RecordIncompatibility here.
        effects = listOf(CastSideEffect.ResumeLocalPlayer),
    )
}

