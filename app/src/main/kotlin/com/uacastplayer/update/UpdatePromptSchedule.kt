package com.uacastplayer.update

/** A deferred update is offered again, but not on every app launch. */
object UpdatePromptSchedule {
    const val REMINDER_INTERVAL_MILLIS: Long = 3L * 24 * 60 * 60 * 1000

    fun isDue(
        releaseTag: String,
        previouslyPromptedTag: String?,
        lastPromptAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        // Older installs may have saved only the tag, before reminders had a timestamp. Do not
        // suddenly re-open a dialog they had already dismissed; the next release will still show.
        return when {
            releaseTag != previouslyPromptedTag -> true
            lastPromptAtMillis == null -> false
            else -> nowMillis < lastPromptAtMillis ||
                nowMillis - lastPromptAtMillis >= REMINDER_INTERVAL_MILLIS
        }
    }
}
