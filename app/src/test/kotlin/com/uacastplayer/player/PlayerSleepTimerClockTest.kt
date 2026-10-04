package com.uacastplayer.player

import android.os.SystemClock
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/** Uses the production clock, not the virtual coroutine clock injected by timer policy tests. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSleepTimerClockTest {
    @Test fun sleepingDeviceExpiresAtFirstTickAfterWakeInsteadOfRestartingCountdown() = runTest {
        var expirations = 0
        val timer = PlayerSleepTimer(backgroundScope, onExpire = { expirations++ })
        timer.start(3.seconds)
        runCurrent()
        val uptime = SystemClock.uptimeMillis()
        val elapsed = SystemClock.elapsedRealtime()

        ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(4))
        assertEquals("Control: CPU uptime stops during deep sleep", uptime, SystemClock.uptimeMillis())
        assertEquals(elapsed + 4_000, SystemClock.elapsedRealtime())
        // We do not claim a coroutine can wake a sleeping OS; this is its first tick after wake.
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals("The elapsed deadline passed while the phone slept", 1, expirations)
        assertNull(timer.remainingMillis.value)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, expirations)
    }
}
