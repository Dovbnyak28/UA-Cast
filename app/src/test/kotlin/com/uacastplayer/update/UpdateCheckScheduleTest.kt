package com.uacastplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckScheduleTest {

    private val day = UpdateCheckSchedule.INTERVAL_MILLIS
    private val now = 1_800_000_000_000L

    @Test
    fun aDeviceThatHasNeverCheckedIsDue() {
        assertTrue(UpdateCheckSchedule.isDue(lastCheckAtMillis = null, nowMillis = now))
    }

    @Test
    fun notDueUntilTheFullDayHasPassed() {
        assertFalse(UpdateCheckSchedule.isDue(now - day + 1, now))
        assertFalse(UpdateCheckSchedule.isDue(now, now))
        assertTrue(UpdateCheckSchedule.isDue(now - day, now))
        assertTrue(UpdateCheckSchedule.isDue(now - day - 1, now))
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
    fun aFailureRetriesAfterOneHour() {
        assertFalse(UpdateCheckSchedule.isDue(now - 59 * 60 * 1000L, now, lastCheckFailed = true))
        assertTrue(UpdateCheckSchedule.isDue(now - 60 * 60 * 1000L, now, lastCheckFailed = true))
    }

    @Test
    fun theNormalIntervalIsOneDay() {
        assertTrue(day == 24L * 60 * 60 * 1000)
    }
}
