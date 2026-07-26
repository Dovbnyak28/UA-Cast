package com.uacastplayer.log

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogBufferTest {

    @Before
    @After
    fun resetBuffer() {
        LogBuffer.clear()
    }

    @Test
    fun `overflowing the entry count evicts the oldest entries first`() {
        repeat(600) { i -> LogBuffer.record(LogLevel.DEBUG, "tag", "entry-$i") }

        val snapshot = LogBuffer.snapshot()

        assertEquals(500, snapshot.size)
        assertEquals("entry-100", snapshot.first().message) // the first 100 (0..99) were evicted
        assertEquals("entry-599", snapshot.last().message)
    }

    @Test
    fun `overflowing the total character budget evicts the oldest entries first`() {
        val bigMessage = "x".repeat(50_000)
        repeat(10) { LogBuffer.record(LogLevel.ERROR, "tag", bigMessage) } // 500,000 chars > 256KB cap

        val snapshot = LogBuffer.snapshot()

        assertTrue("expected the byte cap to have evicted at least the earliest entries", snapshot.size < 10)
        val totalChars = snapshot.sumOf { it.message.length }
        assertTrue("total retained chars ($totalChars) must stay within the 256KB cap", totalChars <= 256 * 1024)
    }

    @Test
    fun `buffer size never grows past the caps regardless of how much is recorded`() {
        repeat(5_000) { i -> LogBuffer.record(LogLevel.WARN, "tag", "message-$i") }

        assertTrue(LogBuffer.snapshot().size <= 500)
    }

    @Test
    fun `concurrent writers never corrupt the buffer`() {
        val writerCount = 20
        val perWriter = 200
        val pool = Executors.newFixedThreadPool(writerCount)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(writerCount)

        repeat(writerCount) { writerIndex ->
            pool.execute {
                startGate.await()
                repeat(perWriter) { i -> LogBuffer.record(LogLevel.DEBUG, "writer-$writerIndex", "msg-$i") }
                doneGate.countDown()
            }
        }
        startGate.countDown()
        assertTrue("writers did not finish in time", doneGate.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        // No crash and no torn state: the snapshot is a well-formed list within both caps.
        val snapshot = LogBuffer.snapshot()
        assertTrue(snapshot.size <= 500)
        assertTrue(snapshot.sumOf { it.message.length } <= 256 * 1024)
    }
}
