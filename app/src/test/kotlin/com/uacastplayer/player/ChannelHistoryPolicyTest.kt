package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelHistoryPolicyTest {

    @Test
    fun `the first switch has no previous channel`() {
        val state = ChannelHistoryPolicy.onSwitch(ChannelHistoryPolicy.State(null, null), newIndex = 0)
        assertEquals(ChannelHistoryPolicy.State(current = 0, previous = null), state)
    }

    @Test
    fun `a switch to a different channel moves the old current into previous`() {
        val afterFirst = ChannelHistoryPolicy.State(current = 0, previous = null)
        val state = ChannelHistoryPolicy.onSwitch(afterFirst, newIndex = 3)
        assertEquals(ChannelHistoryPolicy.State(current = 3, previous = 0), state)
    }

    @Test
    fun `switching to the same channel that is already current does not clobber previous`() {
        val state = ChannelHistoryPolicy.State(current = 3, previous = 0)
        assertEquals(state, ChannelHistoryPolicy.onSwitch(state, newIndex = 3))
    }

    @Test
    fun `switching back to the previous channel swaps current and previous, like a remote's last-channel button`() {
        val state = ChannelHistoryPolicy.State(current = 3, previous = 0)
        val switchedBack = ChannelHistoryPolicy.onSwitch(state, newIndex = 0)
        assertEquals(ChannelHistoryPolicy.State(current = 0, previous = 3), switchedBack)

        // Pressing it again jumps right back, same as a real remote.
        val switchedBackAgain = ChannelHistoryPolicy.onSwitch(switchedBack, newIndex = 3)
        assertEquals(ChannelHistoryPolicy.State(current = 3, previous = 0), switchedBackAgain)
    }
}
