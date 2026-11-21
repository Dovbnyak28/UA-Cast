package com.uacastplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRetrySchedulerTest {
    @Test fun aReplacementCancelsThePreviousDelayAndRunsExactlyOnce() = runTest {
        var retries = 0
        val scheduler = PlayerRetryScheduler(this, {}, { retries++ })
        scheduler.after(1000)
        runCurrent()
        scheduler.after(50)
        advanceUntilIdle()
        assertEquals(1, retries)
    }
    @Test fun cancellationPreventsADeferredNetworkRetry() = runTest {
        var retries = 0
        val scheduler = PlayerRetryScheduler(this, { delay(1000) }, { retries++ })
        scheduler.whenNetworkAvailable()
        runCurrent()
        scheduler.cancel()
        advanceUntilIdle()
        assertEquals(0, retries)
    }
    @Test fun networkWaitAndOrdinaryDelayShareOneOwner() = runTest {
        var retries = 0
        val scheduler = PlayerRetryScheduler(this, { delay(1000) }, { retries++ })
        scheduler.whenNetworkAvailable()
        runCurrent()
        scheduler.after(100)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, retries)
        advanceUntilIdle()
        assertEquals(1, retries)
    }
}
