package com.uacastplayer.cast

/** Reduces the outcome of a direct-to-receiver load request. */
object CastLoadResultReducer {

    fun reduce(state: CastPlaybackState, result: CastLoadResult): CastReducerResult = when (result) {
        CastLoadResult.Success -> CastReducerResult(
            state = state.copy(loadPhase = CastLoadPhase.LOADED),
            effects = listOf(CastSideEffect.PauseLocalPlayer),
        )

        is CastLoadResult.Failure -> CastReducerResult(
            state = state.copy(loadPhase = CastLoadPhase.FAILED),
            effects = listOf(
                CastSideEffect.RecordIncompatibility(result.reason),
                CastSideEffect.ResumeLocalPlayer,
            ),
        )
    }
}
