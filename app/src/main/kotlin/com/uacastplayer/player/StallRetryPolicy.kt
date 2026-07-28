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
 * reliably killing its own recovery and then blaming the stream for it. Live IPTV sources drop and
 * recover connections constantly; the correct terminal state for a silent stall is "keep trying
 * more slowly forever", not "give up" - [onStall] never returns anything but a next attempt.
 */
object StallRetryPolicy {

    private const val BACKOFF_1_MILLIS = 2_000L
    private const val BACKOFF_2_MILLIS = 4_000L
    private const val BACKOFF_3_MILLIS = 8_000L
    private const val BACKOFF_4_MILLIS = 16_000L
    private val BACKOFF_DELAYS_MILLIS = listOf(BACKOFF_1_MILLIS, BACKOFF_2_MILLIS, BACKOFF_3_MILLIS, BACKOFF_4_MILLIS)
    private const val STEADY_STATE_DELAY_MILLIS = 30_000L
    private const val RESET_AFTER_HEALTHY_MILLIS = 60_000L

    /** Two light recoveries in a row not helping is a signal the player's internal state (not just
     * the network) may need a harder reset - every 3rd attempt goes heavy, then back to light. */
    private const val HEAVY_RECOVERY_EVERY_NTH_ATTEMPT = 3

    /** [PlayerUiState.stallRecoveryAttempt] reaching this is when the UI adds a "pick another
     * channel" escape hatch alongside the automatic retries (which keep going regardless). */
    const val CHANNEL_PICKER_HINT_ATTEMPT = 3

    data class State(val attempt: Int = 0, val lastRecoveryAtMillis: Long? = null)

    data class Decision(val delayMillis: Long, val newState: State)

    /**
     * Call each time [StallDetectionPolicy] reports a stall. [nowMillis] measures against
     * [State.lastRecoveryAtMillis] to decide whether this is a fresh problem (the stream played
     * fine for a full minute since the last recovery) or a continuation of the same one - only a
     * fresh problem restarts the fast backoff; a stream that stalls every 40s never gets to.
     */
    fun onStall(nowMillis: Long, state: State): Decision {
        val elapsedSinceLastRecovery = state.lastRecoveryAtMillis?.let { nowMillis - it }
        val attempt = if (elapsedSinceLastRecovery != null && elapsedSinceLastRecovery >= RESET_AFTER_HEALTHY_MILLIS) {
            0
        } else {
            state.attempt
        }
        val delay = BACKOFF_DELAYS_MILLIS.getOrElse(attempt) { STEADY_STATE_DELAY_MILLIS }
        return Decision(delay, State(attempt = attempt + 1, lastRecoveryAtMillis = nowMillis))
    }

    /** [attempt] is the 1-indexed value a [Decision.newState] carries after [onStall] - i.e. "this
     * is recovery attempt number N", not a 0-indexed count. */
    fun recoveryKindFor(attempt: Int): StallRecoveryKind =
        if (attempt % HEAVY_RECOVERY_EVERY_NTH_ATTEMPT == 0) StallRecoveryKind.HEAVY else StallRecoveryKind.LIGHT
}
