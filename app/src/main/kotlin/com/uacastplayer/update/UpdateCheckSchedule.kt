package com.uacastplayer.update

/**
 * When an automatic update check is allowed to run.
 *
 * The check is driven by the app being opened rather than by a background worker: an app nobody
 * opens has nothing to gain from a notification, and this keeps the feature free of a periodic job,
 * a notification permission asked for up front, and the per-manufacturer background limits that
 * make such jobs unreliable anyway.
 */
object UpdateCheckSchedule {

    const val INTERVAL_MILLIS: Long = 24L * 60 * 60 * 1000
    const val FAILURE_RETRY_MILLIS: Long = 60L * 60 * 1000

    /**
     * True when the applicable normal or failure-retry interval has passed since [lastCheckAtMillis],
     * which is also the
     * case when there has never been a check.
     *
     * A timestamp in the future counts as due. It is not a hypothetical: the stored value is wall
     * clock, so a device whose date was wrong and then corrected - or a user who moved the clock
     * forward and back - can leave a timestamp years ahead. Treating that as "checked recently"
     * would disable update checks on that device permanently and silently, which is a far worse
     * failure than one extra request.
     */
    fun isDue(lastCheckAtMillis: Long?, nowMillis: Long, lastCheckFailed: Boolean = false): Boolean {
        if (lastCheckAtMillis == null) return true
        val clockWentBackwards = nowMillis < lastCheckAtMillis
        val interval = if (lastCheckFailed) FAILURE_RETRY_MILLIS else INTERVAL_MILLIS
        return clockWentBackwards || nowMillis - lastCheckAtMillis >= interval
    }
}
