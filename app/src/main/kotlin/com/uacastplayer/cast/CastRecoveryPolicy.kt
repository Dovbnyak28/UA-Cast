package com.uacastplayer.cast

/** What to do about a receiver that went IDLE (ERROR, or FINISHED - which for this app's
 * exclusively-live channels always means an unexpected drop, never a legitimate end of content). */
sealed interface CastRecoveryDecision {
    data class Reload(val attempt: Int, val backoffMillis: Long) : CastRecoveryDecision
    data object GiveUp : CastRecoveryDecision
    data object Ignore : CastRecoveryDecision
}

/**
 * A receiver that goes idle mid-playback is often just a momentary hiccup (a brief network blip on
 * the TV's own Wi-Fi, an origin server hiccup) rather than a real failure - reloading the exact
 * same channel a few seconds later frequently just works, and is far less disruptive than falling
 * back to local playback and making the user re-select the channel on the TV. This is purely about
 * *whether and when* to retry; the actual reload (same delivery mode, same URL) is
 * [com.uacastplayer.cast.CastSessionRepository]'s job.
 */
object CastRecoveryPolicy {
    /** Number of fast-backoff attempts before reloads settle into [STEADY_STATE_BACKOFF_MILLIS] -
     * no longer a hard ceiling (see [onReceiverIdle]'s KDoc for why a live cast route must never
     * finally give up on its own). */
    const val MAX_ATTEMPTS = 3
    const val STABLE_PLAYING_RESET_MILLIS = 60_000L
    private const val BACKOFF_1_MILLIS = 2_000L
    private const val BACKOFF_2_MILLIS = 4_000L
    private const val BACKOFF_3_MILLIS = 8_000L
    private const val STEADY_STATE_BACKOFF_MILLIS = 30_000L
    private val BACKOFF_MILLIS = listOf(BACKOFF_1_MILLIS, BACKOFF_2_MILLIS, BACKOFF_3_MILLIS)

    /**
     * [attemptsSoFar] is the number of reload attempts already made in the current failure episode
     * (0 for the first failure since the channel started, or since the last [STABLE_PLAYING_RESET_MILLIS]
     * of continuous PLAYING - see [shouldResetAttemptCounter]). [isConfirmedIncompatible] mirrors
     * [CastPlaybackState.codecIncompatibility] being non-null: a confirmed MPEG-2 verdict means
     * reloading can never help (see [com.uacastplayer.proxy.RawTsRemuxActivation]'s own doc), so
     * there's nothing to retry - this is the *only* case that ends in [CastRecoveryDecision.GiveUp].
     * Everything else keeps reloading forever: past [MAX_ATTEMPTS] fast attempts, reloads continue
     * at a steady [STEADY_STATE_BACKOFF_MILLIS] rather than stopping - a receiver dropping to IDLE
     * is exactly as routine for live IPTV as the local player's own silent stalls (see
     * [com.uacastplayer.player.StallRetryPolicy]'s KDoc for the identical reasoning), and a route
     * that already loaded once deserves the same infinite patience a flaky network connection does.
     * [selfInitiated] mirrors the same flag [CastReceiverStatusReducer.reduce] uses - an IDLE this
     * app itself caused (by issuing a new load) is not a failure to recover from at all.
     */
    fun onReceiverIdle(
        idleReason: IdleReason,
        isConfirmedIncompatible: Boolean,
        attemptsSoFar: Int,
        selfInitiated: Boolean,
    ): CastRecoveryDecision {
        val isRecoverableReason = idleReason == IdleReason.ERROR || idleReason == IdleReason.FINISHED
        return when {
            selfInitiated || !isRecoverableReason -> CastRecoveryDecision.Ignore
            isConfirmedIncompatible -> CastRecoveryDecision.GiveUp
            else -> {
                val nextAttempt = attemptsSoFar + 1
                val backoff = BACKOFF_MILLIS.getOrElse(attemptsSoFar) { STEADY_STATE_BACKOFF_MILLIS }
                CastRecoveryDecision.Reload(attempt = nextAttempt, backoffMillis = backoff)
            }
        }
    }

    /** Whether [stablePlayingMillis] of continuous PLAYING is long enough that a *new* failure
     * should get a fresh attempt budget instead of continuing to count against whatever attempts
     * were already spent on a previous, now long-resolved episode. */
    fun shouldResetAttemptCounter(stablePlayingMillis: Long): Boolean =
        stablePlayingMillis >= STABLE_PLAYING_RESET_MILLIS
}
