package com.uacastplayer.data.icons

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IconResolutionCoordinatorTest {
    @Test fun completedResolutionsRetainNoPerChannelEntries() = runTest {
        val coordinator = IconResolutionCoordinator()
        repeat(1_000) { index -> coordinator.withResolution("channel-$index", 0L) {} }
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun sameChannelWaitsButDifferentChannelAndGenerationAreIndependent() = runTest {
        val coordinator = IconResolutionCoordinator()
        val release = CompletableDeferred<Unit>()
        var waiterEntered = false
        val owner = async { coordinator.withResolution("shared", 0L) { release.await() } }
        runCurrent()
        val waiter = async { coordinator.withResolution("shared", 0L) { waiterEntered = true } }
        runCurrent()
        assertFalse(waiterEntered)
        coordinator.withResolution("other", 0L) {}
        coordinator.withResolution("shared", 1L) {}
        assertEquals(1, coordinator.entryCountForTesting())
        release.complete(Unit)
        listOf(owner, waiter).awaitAll()
        assertTrue(waiterEntered)
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun cancellationOfAWaitingCallerDoesNotRetireTheActiveLane() = runTest {
        val coordinator = IconResolutionCoordinator()
        val release = CompletableDeferred<Unit>()
        val owner = async { coordinator.withResolution("shared", 0L) { release.await() } }
        runCurrent()
        val waiter = async { coordinator.withResolution("shared", 0L) { error("cancelled waiter entered") } }
        runCurrent()
        waiter.cancelAndJoin()
        assertEquals(1, coordinator.entryCountForTesting())
        release.complete(Unit)
        owner.await()
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun failedResolutionDoesNotLeakALaneOrBlockTheNextCaller() = runTest {
        val coordinator = IconResolutionCoordinator()
        val failure = runCatching { coordinator.withResolution("shared", 0L) { throw IOException("fixture") } }
        assertTrue(failure.exceptionOrNull() is IOException)
        assertEquals(0, coordinator.entryCountForTesting())
        assertEquals("retry", coordinator.withResolution("shared", 0L) { "retry" })
    }
}
