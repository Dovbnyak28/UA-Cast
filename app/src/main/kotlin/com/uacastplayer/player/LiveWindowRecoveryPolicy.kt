package com.uacastplayer.player

private const val RECOVERY_WINDOW_MILLIS = 60_000L
private const val MAX_RECOVERIES_IN_WINDOW = 3

/**
 * Guards `BehindLiveWindowException`'s standard recovery (`seekToDefaultPosition()` + `prepare()`,
 * see [ERROR_CODE_BEHIND_LIVE_WINDOW][androidx.media3.common.PlaybackException]) against looping
 * forever on a stream whose live window keeps slipping out from under playback faster than it can
 * catch up. After [MAX_RECOVERIES_IN_WINDOW] recoveries within [RECOVERY_WINDOW_MILLIS], this is no
 * longer "recoverable" - it's a genuinely broken/misbehaving origin - so it falls through to the
 * normal error path instead of silently retrying forever.
 */
object LiveWindowRecoveryPolicy {

    sealed interface Decision {
        /** [newHistory] is the recovery-attempt timestamp history to keep for the next call -
         * already pruned to [RECOVERY_WINDOW_MILLIS] and with this attempt appended. */
        data class Recover(val newHistory: List<Long>) : Decision
        data object GiveUp : Decision
    }

    fun onBehindLiveWindow(nowMillis: Long, history: List<Long>): Decision {
        val recent = history.filter { nowMillis - it < RECOVERY_WINDOW_MILLIS }
        if (recent.size >= MAX_RECOVERIES_IN_WINDOW) return Decision.GiveUp
        return Decision.Recover(recent + nowMillis)
    }
}
