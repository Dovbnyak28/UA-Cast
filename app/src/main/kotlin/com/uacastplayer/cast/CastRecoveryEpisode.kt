package com.uacastplayer.cast

/**
 * Mutable state for one channel's recovery episode, deliberately independent of the Cast SDK.
 *
 * [CastSessionRepository] remains the adapter that schedules coroutines and issues SDK reloads;
 * this class owns only the state-machine inputs that decide the next recovery action. Keeping the
 * attempt counter and PLAYING window together prevents their reset rules from drifting apart.
 */
internal class CastRecoveryEpisode {
    private var attempts = 0
    private var playingSinceMillis: Long? = null

    fun reset() {
        attempts = 0
        playingSinceMillis = null
    }

    /** Applies a receiver status and returns the stable PLAYING duration that preceded it. */
    fun onStatus(status: ReceiverStatus, nowMillis: Long): Long {
        val transition = PlayingWindowPolicy.transition(playingSinceMillis, status, nowMillis)
        if (CastRecoveryPolicy.shouldResetAttemptCounter(transition.stableBeforeTransitionMillis)) {
            attempts = 0
        }
        playingSinceMillis = transition.nextStartMillis
        return transition.stableBeforeTransitionMillis
    }

    fun decisionFor(
        idleReason: IdleReason,
        isConfirmedIncompatible: Boolean,
        selfInitiated: Boolean,
    ): CastRecoveryDecision = CastRecoveryPolicy.onReceiverIdle(
        idleReason = idleReason,
        isConfirmedIncompatible = isConfirmedIncompatible,
        attemptsSoFar = attempts,
        selfInitiated = selfInitiated,
    )

    fun scheduled(decision: CastRecoveryDecision.Reload) {
        attempts = decision.attempt
    }
}
