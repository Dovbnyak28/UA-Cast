package com.uacastplayer.data.epg

import com.uacastplayer.epg.DecodedEpgSnapshot
import com.uacastplayer.epg.EpgSnapshotHeader
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class EpgSnapshotHandoffTest {
    @Test fun `cancellation before delivery closes the unclaimed legacy document`() {
        verifyCancelledDelivery(TrackingStream())
    }

    @Test fun `a close error does not replace cancellation during delivery`() {
        verifyCancelledDelivery(TrackingStream(failOnClose = true))
    }

    private fun verifyCancelledDelivery(stream: TrackingStream) {
        val caller = QueuedDispatcher()
        val io = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + caller)
        var returned = false
        try {
            val job = scope.launch {
                readEpgSnapshotOnIo(io) { document(stream) }
                returned = true
            }
            var cause: Throwable? = null
            job.invokeOnCompletion { cause = it }
            caller.next().run()
            io.next().run() // Read finished; caller has not received the open stream.
            assertEquals(0, stream.closes)
            job.cancel()
            caller.next().run()

            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled)
            assertTrue("cleanup must preserve cancellation", cause is CancellationException)
            assertFalse(returned)
            assertEquals("no caller accepted ownership of this stream", 1, stream.closes)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `normal delivery leaves the document open for its caller`() {
        val caller = QueuedDispatcher()
        val io = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + caller)
        val stream = TrackingStream()
        val expected = document(stream)
        var actual: DecodedEpgSnapshot? = null
        try {
            val job = scope.launch { actual = readEpgSnapshotOnIo(io) { expected } }
            caller.next().run()
            io.next().run()
            caller.next().run()

            assertTrue(job.isCompleted)
            assertFalse(job.isCancelled)
            assertSame(expected, actual)
            assertEquals(0, stream.closes)
            (actual as DecodedEpgSnapshot.Document).documentStream.close()
            assertEquals(1, stream.closes)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `cancellation before IO starts never opens a document`() {
        val caller = QueuedDispatcher()
        val io = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + caller)
        var reads = 0
        try {
            val job = scope.launch { readEpgSnapshotOnIo(io) { reads++; null } }
            caller.next().run()
            job.cancel()
            io.next().run()
            caller.next().run()
            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled)
            assertEquals(0, reads)
        } finally {
            scope.cancel()
        }
    }

    private fun document(stream: TrackingStream) =
        DecodedEpgSnapshot.Document(EpgSnapshotHeader("source", 1L), stream)

    private class TrackingStream(private val failOnClose: Boolean = false) : ByteArrayInputStream(byteArrayOf()) {
        var closes = 0
        override fun close() {
            closes++
            if (failOnClose) throw IOException("fixture close failure")
            super.close()
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun next(): Runnable = queue.removeFirst()
    }
}
