package com.uacastplayer.data.epg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSnapshotMutationCoordinatorTest {
    @Test
    fun `new source waits behind physical write and becomes final snapshot writer`() = runTest {
        val coordinator = EpgSnapshotMutationCoordinator()
        val oldLease = coordinator.begin()
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()

        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.runWriteIfCurrent(oldLease) {
                oldStarted.complete(Unit)
                withContext(NonCancellable) { releaseOld.await() }
                writes += "old"
            }
        }
        oldStarted.await()
        old.cancel()
        val newLease = coordinator.begin()
        val newer = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.runWriteIfCurrent(newLease) { writes += "new" }
        }

        releaseOld.complete(Unit)
        old.join()

        assertTrue(newer.await())
        assertEquals(listOf("old", "new"), writes)
    }

    @Test
    fun `superseded source cannot start a late snapshot write`() = runTest {
        val coordinator = EpgSnapshotMutationCoordinator()
        val oldLease = coordinator.begin()
        coordinator.begin()

        val ran = coordinator.runWriteIfCurrent(oldLease) { error("stale EPG write ran") }

        assertFalse(ran)
    }
}
