package com.uacastplayer.dlna

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestDlnaAttemptSerializerTest {

    @Test
    fun `new attempt waits for blocking predecessor and predecessor stops at checkpoint`() = runTest {
        val serializer = LatestDlnaAttemptSerializer()
        val currentGeneration = AtomicLong(1)
        val releaseBlockingCall = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(1, currentGeneration::get) { checkpoint ->
                events += "first-started"
                firstEntered.complete(Unit)
                // Models a synchronous SOAP call: cancelling its Job does not finish the call.
                withContext(NonCancellable) { releaseBlockingCall.await() }
                checkpoint()
                events += "stale-side-effect"
            }
        }
        firstEntered.await()
        currentGeneration.set(2)
        first.cancel()

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(2, currentGeneration::get) { _ ->
                events += "second-started"
                "connected"
            }
        }
        assertFalse("new attempt bypassed the still-running SOAP call", second.isCompleted)

        releaseBlockingCall.complete(Unit)
        first.join()

        assertEquals("connected", second.await())
        assertEquals(listOf("first-started", "second-started"), events)
    }

    @Test
    fun `queued teardown runs before a later connect attempt`() = runTest {
        val serializer = LatestDlnaAttemptSerializer()
        val currentGeneration = AtomicLong(1)
        val releaseBlockingCall = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(1, currentGeneration::get) { checkpoint ->
                events += "old-connect"
                firstEntered.complete(Unit)
                withContext(NonCancellable) { releaseBlockingCall.await() }
                checkpoint()
            }
        }
        firstEntered.await()
        currentGeneration.set(2)
        first.cancel()
        val teardown = async(start = CoroutineStart.UNDISPATCHED) {
            serializer.runSerialized { events += "old-stop" }
        }
        val latest = async(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(2, currentGeneration::get) { events += "new-connect" }
        }

        releaseBlockingCall.complete(Unit)
        first.join()
        teardown.await()
        latest.await()

        assertEquals(listOf("old-connect", "old-stop", "new-connect"), events)
    }

    @Test
    fun `newer volume command is the last side effect sent to renderer`() = runTest {
        val serializer = LatestDlnaAttemptSerializer()
        val currentGeneration = AtomicLong(1)
        val releaseOldSet = CompletableDeferred<Unit>()
        val oldSetEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val oldSet = launch(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(1, currentGeneration::get) { checkpoint ->
                events += "set-20"
                oldSetEntered.complete(Unit)
                withContext(NonCancellable) { releaseOldSet.await() }
                checkpoint()
                events += "read-back-20"
            }
        }
        oldSetEntered.await()
        currentGeneration.set(2)
        val newSet = async(start = CoroutineStart.UNDISPATCHED) {
            serializer.run(2, currentGeneration::get) { events += "set-80" }
        }

        releaseOldSet.complete(Unit)
        oldSet.join()
        newSet.await()

        assertEquals(listOf("set-20", "set-80"), events)
    }

    @Test
    fun `stop cannot enter lifecycle while current proxy setup is incomplete`() {
        val serializer = LatestDlnaAttemptSerializer()
        val currentGeneration = AtomicLong(1)
        val setupEntered = CountDownLatch(1)
        val releaseSetup = CountDownLatch(1)
        val stopWaiting = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val events = mutableListOf<String>()

        val setupThread = thread {
            runCatching {
                serializer.withCurrentLifecycle(1, currentGeneration::get) {
                    events += "setup-started"
                    setupEntered.countDown()
                    releaseSetup.await()
                    events += "setup-finished"
                }
            }.exceptionOrNull()?.let(failure::set)
        }
        assertTrue("proxy setup did not start", setupEntered.await(2, TimeUnit.SECONDS))

        currentGeneration.incrementAndGet()
        val stopThread = thread {
            stopWaiting.countDown()
            serializer.withLifecycle { events += "stop" }
        }
        assertTrue("stop thread did not start", stopWaiting.await(2, TimeUnit.SECONDS))
        releaseSetup.countDown()
        setupThread.join()
        stopThread.join()

        assertNull(failure.get())
        assertEquals(listOf("setup-started", "setup-finished", "stop"), events)
    }

    @Test
    fun `stale setup cannot restart proxy after stop owns newer generation`() {
        val serializer = LatestDlnaAttemptSerializer()
        val currentGeneration = AtomicLong(2)
        val events = mutableListOf<String>()

        serializer.withLifecycle { events += "stop" }
        val failure = runCatching {
            serializer.withCurrentLifecycle(1, currentGeneration::get) { events += "stale-setup" }
        }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(listOf("stop"), events)
    }
}
