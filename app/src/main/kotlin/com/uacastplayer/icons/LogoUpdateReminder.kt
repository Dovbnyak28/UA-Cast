package com.uacastplayer.icons

/** Nudges the user to refresh the icon pack roughly every 10 days. */
object LogoUpdateReminder {
    const val INTERVAL_MILLIS = 10L * 24 * 3_600_000L

    fun isDue(lastPrefetchAtMillis: Long?, nowMillis: Long): Boolean =
        lastPrefetchAtMillis == null || nowMillis - lastPrefetchAtMillis >= INTERVAL_MILLIS
}
