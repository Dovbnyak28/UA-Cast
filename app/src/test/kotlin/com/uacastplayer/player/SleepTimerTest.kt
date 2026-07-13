package com.uacastplayer.player

import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerCalculatorTest {

    @Test
    fun `end time is now plus the duration`() {
        val end = SleepTimerCalculator.endTimeMillis(nowMillis = 1_000L, duration = 15.minutes)

        assertEquals(1_000L + 15.minutes.inWholeMilliseconds, end)
    }

    @Test
    fun `remaining time counts down to zero and never goes negative`() {
        val end = 10_000L

        assertEquals(10_000L, SleepTimerCalculator.remainingMillis(nowMillis = 0L, endTimeMillis = end))
        assertEquals(4_000L, SleepTimerCalculator.remainingMillis(nowMillis = 6_000L, endTimeMillis = end))
        assertEquals(0L, SleepTimerCalculator.remainingMillis(nowMillis = 12_000L, endTimeMillis = end))
    }

    @Test
    fun `expiry is exclusive of the moment it ends`() {
        assertFalse(SleepTimerCalculator.hasExpired(nowMillis = 999L, endTimeMillis = 1_000L))
        assertTrue(SleepTimerCalculator.hasExpired(nowMillis = 1_000L, endTimeMillis = 1_000L))
        assertTrue(SleepTimerCalculator.hasExpired(nowMillis = 1_001L, endTimeMillis = 1_000L))
    }
}

class SleepTimerFormatterTest {

    @Test
    fun `formats whole minutes with zero-padded seconds`() {
        assertEquals("15:00", SleepTimerFormatter.formatRemaining(15.minutes.inWholeMilliseconds))
    }

    @Test
    fun `formats sub-minute remainders`() {
        assertEquals("0:09", SleepTimerFormatter.formatRemaining(9_000L))
    }

    @Test
    fun `rounds down to the nearest second`() {
        assertEquals("0:59", SleepTimerFormatter.formatRemaining(59_999L))
    }
}
