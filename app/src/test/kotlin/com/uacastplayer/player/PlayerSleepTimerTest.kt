package com.uacastplayer.player

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerSleepTimerTest {
    @Test fun `expires exactly once without any UI collector`() = runTest {
        var expirations = 0
        val timer = PlayerSleepTimer(this, { testScheduler.currentTime }) { expirations++ }
        timer.start(3.seconds)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, expirations)
        assertNull(timer.remainingMillis.value)
        advanceTimeBy(10_000)
        assertEquals(1, expirations)
    }

    @Test fun `replacement cancels old deadline and explicit close cancels the new deadline`() = runTest {
        var expirations = 0
        val timer = PlayerSleepTimer(this, { testScheduler.currentTime }) { expirations++ }
        timer.start(2.seconds)
        advanceTimeBy(1_000)
        timer.start(5.seconds)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, expirations)
        timer.cancel()
        advanceTimeBy(10_000)
        assertNull(timer.remainingMillis.value)
        assertEquals(0, expirations)
    }

    @Test fun `destroying owner cancels deadline`() = runTest {
        val owner = Job()
        var expirations = 0
        val timer = PlayerSleepTimer(
            CoroutineScope(coroutineContext + owner), { testScheduler.currentTime },
        ) { expirations++ }
        timer.start(2.seconds)
        runCurrent()
        owner.cancel()
        advanceTimeBy(3_000)
        assertEquals(0, expirations)
    }
}
