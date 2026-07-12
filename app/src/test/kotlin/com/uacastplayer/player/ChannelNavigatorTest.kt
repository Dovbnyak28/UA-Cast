package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigatorTest {

    @Test
    fun `nextIndex advances by one within bounds`() {
        assertEquals(2, ChannelNavigator.nextIndex(1, count = 5, wrapAround = false))
    }

    @Test
    fun `nextIndex returns null at the end without wrap-around`() {
        assertNull(ChannelNavigator.nextIndex(4, count = 5, wrapAround = false))
    }

    @Test
    fun `nextIndex wraps to zero at the end with wrap-around`() {
        assertEquals(0, ChannelNavigator.nextIndex(4, count = 5, wrapAround = true))
    }

    @Test
    fun `previousIndex retreats by one within bounds`() {
        assertEquals(1, ChannelNavigator.previousIndex(2, count = 5, wrapAround = false))
    }

    @Test
    fun `previousIndex returns null at the start without wrap-around`() {
        assertNull(ChannelNavigator.previousIndex(0, count = 5, wrapAround = false))
    }

    @Test
    fun `previousIndex wraps to the last index at the start with wrap-around`() {
        assertEquals(4, ChannelNavigator.previousIndex(0, count = 5, wrapAround = true))
    }

    @Test
    fun `empty channel list yields null for every direction`() {
        assertNull(ChannelNavigator.nextIndex(0, count = 0, wrapAround = true))
        assertNull(ChannelNavigator.previousIndex(0, count = 0, wrapAround = true))
        assertNull(ChannelNavigator.nextPlayableIndex(0, count = 0, wrapAround = true) { false })
    }

    @Test
    fun `nextPlayableIndex skips over dead channels`() {
        val deadIndices = setOf(1, 2)
        val result = ChannelNavigator.nextPlayableIndex(0, count = 5, wrapAround = false) { it in deadIndices }
        assertEquals(3, result)
    }

    @Test
    fun `nextPlayableIndex wraps past dead channels when wrap-around is enabled`() {
        val deadIndices = setOf(0, 1)
        val result = ChannelNavigator.nextPlayableIndex(4, count = 5, wrapAround = true) { it in deadIndices }
        assertEquals(2, result)
    }

    @Test
    fun `nextPlayableIndex returns null when every channel is dead`() {
        val result = ChannelNavigator.nextPlayableIndex(0, count = 5, wrapAround = true) { true }
        assertNull(result)
    }

    @Test
    fun `nextPlayableIndex returns null without wrap-around once it reaches the end`() {
        val deadIndices = setOf(3, 4)
        val result = ChannelNavigator.nextPlayableIndex(2, count = 5, wrapAround = false) { it in deadIndices }
        assertNull(result)
    }
}
