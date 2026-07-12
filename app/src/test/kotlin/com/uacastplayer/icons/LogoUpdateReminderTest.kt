package com.uacastplayer.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoUpdateReminderTest {

    @Test
    fun `is due when there has never been a prefetch`() {
        assertTrue(LogoUpdateReminder.isDue(null, nowMillis = 0L))
    }

    @Test
    fun `is not due before 10 days have passed`() {
        assertFalse(LogoUpdateReminder.isDue(lastPrefetchAtMillis = 0L, nowMillis = LogoUpdateReminder.INTERVAL_MILLIS - 1))
    }

    @Test
    fun `is due once 10 days have passed`() {
        assertTrue(LogoUpdateReminder.isDue(lastPrefetchAtMillis = 0L, nowMillis = LogoUpdateReminder.INTERVAL_MILLIS))
    }
}
