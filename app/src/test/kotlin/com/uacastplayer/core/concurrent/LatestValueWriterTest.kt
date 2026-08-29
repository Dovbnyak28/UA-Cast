package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestValueWriterTest {

    @Test
    fun `writes never overlap and a busy writer persists the newest submitted state next`() = runTest {
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val firstWriteStarted = CompletableDeferred<Unit>()
        val written = mutableListOf<Int>()
        var activeWrites = 0
        var maximumActiveWrites = 0
        val writer = LatestValueWriter<Int>(
            scope = this,
            write = { value ->
                activeWrites++
                maximumActiveWrites = maxOf(maximumActiveWrites, activeWrites)
                if (value == 1) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                }
                written += value
                activeWrites--
            },
        )

        writer.submit(1)
        runCurrent()
        firstWriteStarted.await()
        writer.submit(2)
        writer.submit(3)
        releaseFirstWrite.complete(Unit)
        runCurrent()

        assertEquals(listOf(1, 3), written)
        assertEquals(1, maximumActiveWrites)
        writer.close()
    }

    @Test
    fun `one failed write does not retire the actor before a later state`() = runTest {
        val attempted = mutableListOf<Int>()
        val persisted = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        val writer = LatestValueWriter<Int>(
            scope = this,
            write = { value ->
                attempted += value
                if (value == 1) throw IllegalStateException("disk provider failed")
                persisted += value
            },
            onWriteFailure = failures::add,
        )

        writer.submit(1)
        runCurrent()
        writer.submit(2)
        runCurrent()

        assertEquals(listOf(1, 2), attempted)
        assertEquals(listOf(2), persisted)
        assertTrue(failures.single() is IllegalStateException)
        writer.close()
    }
}
