package com.uacastplayer.proxy

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The proxy must stay visible in the log without being the only thing in it.
 *
 * The numbers these tests are built around are from two real reports off a Mi A2: 499 of the app's
 * 500 log entries were two proxy sentences, and the ring reached back 24 minutes of a 3-hour
 * session.
 */
class ProxyServeRollupTest {

    private val window = ProxyServeRollup.WINDOW_MILLIS

    @Test
    fun `the first serve of a session is reported at once`() {
        val rollup = ProxyServeRollup()

        val summary = rollup.playlistServed(1_000)

        assertNotNull("the first serve must not wait for a window to close", summary)
        assertEquals(1, summary!!.playlists)
        assertEquals(0L, summary.windowMillis)
    }

    @Test
    fun `serves inside the window are counted, not logged`() {
        val rollup = ProxyServeRollup()
        rollup.playlistServed(0)

        assertNull(rollup.playlistServed(4_000))
        assertNull(rollup.segmentServed(1_000_000, 10_000))
        assertNull(rollup.playlistServed(window - 1))
    }

    @Test
    fun `the window closes on the first serve past it, carrying everything since`() {
        val rollup = ProxyServeRollup()
        rollup.playlistServed(0)
        repeat(14) { rollup.playlistServed(4_000L * (it + 1)) }
        repeat(5) { rollup.segmentServed(2L * 1024 * 1024, 10_000L * (it + 1)) }

        val summary = rollup.segmentServed(2L * 1024 * 1024, window)

        assertNotNull(summary)
        assertEquals(14, summary!!.playlists)
        assertEquals(6, summary.segments)
        assertEquals(12L, summary.bytes / (1024 * 1024))
        assertEquals(window, summary.windowMillis)
    }

    /** The measured shape of a real session, against the ring it has to fit in. Twenty-four minutes
     * cost 499 entries; the same traffic must now cost about one per minute. */
    @Test
    fun `a real session's cadence fits the log ring instead of filling it`() {
        val rollup = ProxyServeRollup()
        var lines = 0
        val minutes = 24
        // A playlist poll every 4s and a segment every 10s, which is what the reports show.
        for (second in 0 until minutes * 60) {
            val now = second * 1000L
            if (second % 4 == 0 && rollup.playlistServed(now) != null) lines++
            if (second % 10 == 0 && rollup.segmentServed(7L * 1024 * 1024, now) != null) lines++
        }

        assertTrue("24 minutes of casting produced $lines log lines", lines <= minutes + 1)
    }

    @Test
    fun `windows abut rather than drift`() {
        val rollup = ProxyServeRollup()
        rollup.playlistServed(0)
        // Arrives late; the next window must start here, not at the deadline it missed.
        rollup.playlistServed(window + 5_000)

        assertNull(rollup.playlistServed(window * 2))
        assertNotNull(rollup.playlistServed(window * 2 + 5_000))
    }

    @Test
    fun `stopping the server does not lose the last window`() {
        val rollup = ProxyServeRollup()
        rollup.playlistServed(0)
        rollup.playlistServed(4_000)
        rollup.segmentServed(3L * 1024 * 1024, 10_000)

        val summary = rollup.flush(12_000)

        assertNotNull(summary)
        assertEquals(1, summary!!.playlists)
        assertEquals(1, summary.segments)
    }

    @Test
    fun `flushing an empty window says nothing`() {
        val rollup = ProxyServeRollup()
        rollup.playlistServed(0)

        assertNull("nothing has been served since the last line", rollup.flush(5_000))
    }

    /**
     * Serves arrive on several of the http server's pool threads at once. A dropped count is not a
     * missing log line, it is a line that understates what the proxy did - so this counts every
     * serve back out of the summaries and requires the total to be exact.
     */
    @Test
    fun `counts survive several threads serving at once`() {
        val rollup = ProxyServeRollup(windowMillis = 0)
        val threads = 8
        val perThread = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val counted = java.util.concurrent.atomic.AtomicInteger()

        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) { i ->
                    rollup.playlistServed(i.toLong())?.let { counted.addAndGet(it.playlists) }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        rollup.flush(0)?.let { counted.addAndGet(it.playlists) }

        assertEquals(threads * perThread, counted.get())
    }

    @Test
    fun `the sentence carries the rate, and names the first serve as one`() {
        val first = ProxyServeSummary(playlists = 1, segments = 0, bytes = 0, windowMillis = 0)
        val window = ProxyServeSummary(
            playlists = 15,
            segments = 6,
            bytes = 42L * 1024 * 1024,
            windowMillis = 60_000,
        )

        assertTrue(first.sentence(), first.sentence().contains("first serve"))
        assertEquals("Proxy served: 15 playlist(s), 6 segment(s), 42MB in 60s", window.sentence())
    }
}
