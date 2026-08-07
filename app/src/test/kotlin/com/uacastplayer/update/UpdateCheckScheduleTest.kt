package com.uacastplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckScheduleTest {

    private val week = UpdateCheckSchedule.INTERVAL_MILLIS
    private val now = 1_800_000_000_000L

    @Test
    fun aDeviceThatHasNeverCheckedIsDue() {
        assertTrue(UpdateCheckSchedule.isDue(lastCheckAtMillis = null, nowMillis = now))
    }

    @Test
    fun notDueUntilTheFullWeekHasPassed() {
        assertFalse(UpdateCheckSchedule.isDue(now - week + 1, now))
        assertFalse(UpdateCheckSchedule.isDue(now, now))
        assertTrue(UpdateCheckSchedule.isDue(now - week, now))
        assertTrue(UpdateCheckSchedule.isDue(now - week - 1, now))
    }

    /**
     * The stored value is wall clock, so a device whose date was wrong and later corrected can hold
     * a timestamp years in the future. Read as "checked recently", that would switch update checks
     * off on that device permanently and without a trace - much worse than one extra request.
     */
    @Test
    fun aTimestampFromTheFutureIsDueRatherThanBlockingForever() {
        assertTrue(UpdateCheckSchedule.isDue(now + 1, now))
        assertTrue(UpdateCheckSchedule.isDue(now + 10L * 365 * 24 * 60 * 60 * 1000, now))
    }

    @Test
    fun theIntervalIsSevenDays() {
        assertTrue(week == 7L * 24 * 60 * 60 * 1000)
    }
}
