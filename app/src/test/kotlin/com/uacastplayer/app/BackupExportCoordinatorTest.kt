package com.uacastplayer.app

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
class BackupExportCoordinatorTest {
    @Test fun exportsToTheSameDestinationWaitButOtherDocumentsRemainIndependent() = runTest {
        val coordinator = BackupExportCoordinator()
        val release = CompletableDeferred<Unit>()
        var waiterEntered = false
        val owner = async { coordinator.withDestination("same") { release.await() } }
        runCurrent()
        val waiter = async { coordinator.withDestination("same") { waiterEntered = true } }
        runCurrent()
        assertFalse(waiterEntered)
        coordinator.withDestination("other") {}
        assertEquals(1, coordinator.entryCountForTesting())
        release.complete(Unit)
        listOf(owner, waiter).awaitAll()
        assertTrue(waiterEntered)
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun cancellingAWaiterDoesNotCancelTheActiveWriterOrRetainItsEntry() = runTest {
        val coordinator = BackupExportCoordinator()
        val release = CompletableDeferred<Unit>()
        val owner = async { coordinator.withDestination("same") { release.await() } }
        runCurrent()
        val waiter = async { coordinator.withDestination("same") { error("cancelled waiter entered") } }
        runCurrent()
        waiter.cancelAndJoin()
        assertEquals(1, coordinator.entryCountForTesting())
        release.complete(Unit)
        owner.await()
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun cancellationOfTheOwnerReleasesTheDestinationForAnActiveWaiter() = runTest {
        val coordinator = BackupExportCoordinator()
        val neverReleased = CompletableDeferred<Unit>()
        val owner = async { coordinator.withDestination("same") { neverReleased.await() } }
        runCurrent()
        val waiter = async { coordinator.withDestination("same") { "new backup" } }
        runCurrent()
        owner.cancelAndJoin()
        assertEquals("new backup", waiter.await())
        assertEquals(0, coordinator.entryCountForTesting())
    }

    @Test fun failedWritesReleaseTheirEntryAndAllowTheNextExport() = runTest {
        val coordinator = BackupExportCoordinator()
        val outcome = runCatching { coordinator.withDestination("same") { throw IOException("fixture") } }
        assertTrue(outcome.exceptionOrNull() is IOException)
        assertEquals(0, coordinator.entryCountForTesting())
        assertEquals("retry", coordinator.withDestination("same") { "retry" })
    }

    @Test fun completedDestinationsDoNotAccumulateIdleEntries() = runTest {
        val coordinator = BackupExportCoordinator()
        repeat(1_000) { index -> coordinator.withDestination("document-$index") {} }
        assertEquals(0, coordinator.entryCountForTesting())
    }
}
