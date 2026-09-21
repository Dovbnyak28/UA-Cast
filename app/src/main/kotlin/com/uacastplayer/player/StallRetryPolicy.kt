package com.uacastplayer.player

/** Which recovery to attempt - see [StallRetryPolicy.recoveryKindFor]. */
enum class StallRecoveryKind { LIGHT, HEAVY }

/**
 * Governs automatic recovery from a *silent* stall (see [StallDetectionPolicy]) - a live stream
 * that never raises a [androidx.media3.common.PlaybackException], so [PlaybackRetryPolicy] (which
 * only reacts to real errors) never runs. Before this policy existed, [PlayerViewModel] treated a
 * second silent stall within 30s of the first recovery as unrecoverable and gave up on the channel
 * outright - but the recovery itself (stop/prepare/play) always re-buffers for a few seconds, and
 * on a slow connection that re-buffering routinely took longer than the 30s window, so the app was
 * reliably killing its own recovery and then blaming the stream for it. Recovery therefore keeps
 * a generous grace period, but has a finite budget: a permanently dead source must not keep the
 * network and decoders busy forever. Only observed playback progress resets the budget, not time
 * spent waiting for a retry or a brief READY callback.
 */
object StallRetryPolicy {

    private const val BACKOFF_1_MILLIS = 2_000L
    private const val BACKOFF_2_MILLIS = 4_000L
    private const val BACKOFF_3_MILLIS = 8_000L
    private const val BACKOFF_4_MILLIS = 16_000L
    private val BACKOFF_DELAYS_MILLIS = listOf(BACKOFF_1_MILLIS, BACKOFF_2_MILLIS, BACKOFF_3_MILLIS, BACKOFF_4_MILLIS)
    private const val STEADY_STATE_DELAY_MILLIS = 30_000L
    private const val RESET_AFTER_HEALTHY_MILLIS = 60_000L
    const val MAX_ATTEMPTS = 8

    /** Two light recoveries in a row not helping is a signal the player's internal state (not just
     * the network) may need a harder reset - every 3rd attempt goes heavy, then back to light. */
    private const val HEAVY_RECOVERY_EVERY_NTH_ATTEMPT = 3

    /** [PlayerUiState.stallRecoveryAttempt] reaching this is when the UI adds a "pick another
     * channel" escape hatch alongside the bounded automatic retries. */
    const val CHANNEL_PICKER_HINT_ATTEMPT = 3

    data class State(val attempt: Int = 0, val healthySinceMillis: Long? = null)

    sealed interface Decision {
        data class Retry(val delayMillis: Long, val newState: State) : Decision
        data object GiveUp : Decision
    }

    /**
     * Sample actual forward progress on the same monotonic clock as stall detection. Buffering,
     * a frozen READY position, and gaps between retries do not count as healthy playback.
     */
    fun onPlaybackSample(nowMillis: Long, isAdvancing: Boolean, state: State): State {
        if (!isAdvancing) return state.copy(healthySinceMillis = null)
        val since = state.healthySinceMillis ?: nowMillis
        return if (nowMillis - since >= RESET_AFTER_HEALTHY_MILLIS) State()
        else state.copy(healthySinceMillis = since)
    }

    fun onStall(state: State): Decision {
        if (state.attempt >= MAX_ATTEMPTS) return Decision.GiveUp
        val delay = BACKOFF_DELAYS_MILLIS.getOrElse(state.attempt) { STEADY_STATE_DELAY_MILLIS }
        return Decision.Retry(delay, State(attempt = state.attempt + 1))
    }

    /** [attempt] is the 1-indexed value a [Decision.Retry.newState] carries after [onStall] - i.e. "this
     * is recovery attempt number N", not a 0-indexed count. */
    fun recoveryKindFor(attempt: Int): StallRecoveryKind =
        if (attempt % HEAVY_RECOVERY_EVERY_NTH_ATTEMPT == 0) StallRecoveryKind.HEAVY else StallRecoveryKind.LIGHT
}
