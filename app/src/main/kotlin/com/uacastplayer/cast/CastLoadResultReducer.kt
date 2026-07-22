package com.uacastplayer.cast

/** Reduces the outcome of a direct-to-receiver load request. */
object CastLoadResultReducer {

    fun reduce(state: CastPlaybackState, result: CastLoadResult): CastReducerResult = when (result) {
        // No PauseLocalPlayer here: CastSessionRepository.loadOnReceiver already emits it
        // synchronously right before issuing the load, not just on success - see that call site's
        // own doc for why (freeing the phone's upstream connection can't wait for this result).
        CastLoadResult.Success -> CastReducerResult(state = state.copy(loadPhase = CastLoadPhase.LOADED))

        is CastLoadResult.Failure -> CastReducerResult(
            state = state.copy(loadPhase = CastLoadPhase.FAILED),
            effects = listOf(
                CastSideEffect.RecordIncompatibility(result.reason),
                CastSideEffect.ResumeLocalPlayer,
            ),
        )
    }
}
