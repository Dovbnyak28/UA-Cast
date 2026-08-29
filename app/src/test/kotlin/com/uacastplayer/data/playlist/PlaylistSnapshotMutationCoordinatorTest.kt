package com.uacastplayer.data.playlist

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

class PlaylistSnapshotMutationCoordinatorTest {
    @Test
    fun `delete waits for non cancellable write and remains final disk mutation`() = runTest {
        val coordinator = PlaylistSnapshotMutationCoordinator()
        val lease = coordinator.captureWrite("source-a")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val mutations = mutableListOf<String>()

        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.runWriteIfCurrent(lease) {
                writeStarted.complete(Unit)
                // Models AtomicFile encoding: cancellation is only observed after the physical
                // write returns, never halfway through it.
                withContext(NonCancellable) { releaseWrite.await() }
                mutations += "write"
            }
        }
        writeStarted.await()
        writer.cancel()
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.invalidateAndDelete("source-a") { mutations += "delete" }
        }

        releaseWrite.complete(Unit)
        writer.join()
        deletion.await()

        assertEquals(listOf("write", "delete"), mutations)
    }

    @Test
    fun `write captured before deletion cannot recreate snapshot afterwards`() = runTest {
        val coordinator = PlaylistSnapshotMutationCoordinator()
        val stale = coordinator.captureWrite("source-a")
        coordinator.invalidateAndDelete("source-a") { }

        val staleRan = coordinator.runWriteIfCurrent(stale) { error("stale write ran") }
        val fresh = coordinator.captureWrite("source-a")
        val freshRan = coordinator.runWriteIfCurrent(fresh) { }

        assertFalse(staleRan)
        assertTrue(freshRan)
    }

    @Test
    fun `idle source entries are bounded after many one-off writes`() = runTest {
        val coordinator = PlaylistSnapshotMutationCoordinator()

        repeat(512) { index ->
            val lease = coordinator.captureWrite("source-$index")
            assertTrue(coordinator.runWriteIfCurrent(lease) { })
        }

        assertTrue(coordinator.entryCountForTesting() <= 256)
    }
}
