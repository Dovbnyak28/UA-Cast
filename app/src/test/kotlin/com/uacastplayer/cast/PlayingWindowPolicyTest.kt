package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayingWindowPolicyTest {

    private companion object {
        const val START = 1_000L
    }

    @Test
    fun `the first PLAYING opens the window at now`() {
        assertEquals(START, PlayingWindowPolicy.next(current = null, ReceiverStatus.PLAYING, nowMillis = START))
    }

    /**
     * The regression this policy exists for. The Cast SDK re-reports PLAYING on unrelated events -
     * volume changes, queue updates, periodic refreshes - so a channel sitting happily on screen
     * emits a stream of identical updates. Restarting the clock on each would pin the measured
     * stretch near zero forever, the recovery attempt counter would never reset, and a channel that
     * had been fine for an hour would give up early on its next genuine hiccup.
     */
    @Test
    fun `a repeated PLAYING keeps the original start rather than restarting the clock`() {
        var window = PlayingWindowPolicy.next(null, ReceiverStatus.PLAYING, START)
        for (later in listOf(START + 500, START + 30_000, START + 3_600_000)) {
            window = PlayingWindowPolicy.next(window, ReceiverStatus.PLAYING, later)
        }
        assertEquals(START, window)
        assertEquals(3_600_000L, PlayingWindowPolicy.stableMillis(window, START + 3_600_000))
    }

    @Test
    fun `anything that is not PLAYING closes the window`() {
        val open = PlayingWindowPolicy.next(null, ReceiverStatus.PLAYING, START)
        for (status in ReceiverStatus.entries.filter { it != ReceiverStatus.PLAYING }) {
            assertNull("$status must close the playing window", PlayingWindowPolicy.next(open, status, START + 1))
        }
    }

    @Test
    fun `a window reopened after a stall starts from the new PLAYING, not the old one`() {
        val first = PlayingWindowPolicy.next(null, ReceiverStatus.PLAYING, START)
        val closed = PlayingWindowPolicy.next(first, ReceiverStatus.BUFFERING, START + 10_000)
        val reopened = PlayingWindowPolicy.next(closed, ReceiverStatus.PLAYING, START + 20_000)
        assertEquals(START + 20_000, reopened)
    }

    @Test
    fun `a closed window reports zero stable millis, not a negative or a huge number`() {
        assertEquals(0L, PlayingWindowPolicy.stableMillis(current = null, nowMillis = START))
    }
}
