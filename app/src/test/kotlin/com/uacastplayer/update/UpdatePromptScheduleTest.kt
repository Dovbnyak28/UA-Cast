package com.uacastplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptScheduleTest {
    private val now = 1_800_000_000_000L
    private val interval = UpdatePromptSchedule.REMINDER_INTERVAL_MILLIS

    @Test
    fun newVersionIsAlwaysOffered() {
        assertTrue(UpdatePromptSchedule.isDue("v2", "v1", now, now))
    }

    @Test
    fun sameVersionWaitsThreeDaysBeforeReminding() {
        assertFalse(UpdatePromptSchedule.isDue("v1", "v1", now - interval + 1, now))
        assertTrue(UpdatePromptSchedule.isDue("v1", "v1", now - interval, now))
    }

    @Test
    fun legacyTagWithoutTimestampDoesNotSuddenlyReopen() {
        assertFalse(UpdatePromptSchedule.isDue("v1", "v1", null, now))
    }

    @Test
    fun clockCorrectionCannotSuppressRemindersForever() {
        assertTrue(UpdatePromptSchedule.isDue("v1", "v1", now + interval, now))
    }
}
