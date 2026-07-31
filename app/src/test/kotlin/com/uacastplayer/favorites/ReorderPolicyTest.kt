package com.uacastplayer.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReorderPolicyTest {

    @Test
    fun `move shifts an item forward`() {
        val result = ReorderPolicy.move(listOf("a", "b", "c", "d"), fromIndex = 0, toIndex = 2)
        assertEquals(listOf("b", "c", "a", "d"), result)
    }

    @Test
    fun `move shifts an item backward`() {
        val result = ReorderPolicy.move(listOf("a", "b", "c", "d"), fromIndex = 3, toIndex = 1)
        assertEquals(listOf("a", "d", "b", "c"), result)
    }

    @Test
    fun `move to the same index returns the same list instance`() {
        val items = listOf("a", "b", "c")
        assertSame(items, ReorderPolicy.move(items, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun `move with an out-of-range index returns the list unchanged`() {
        val items = listOf("a", "b", "c")
        assertSame(items, ReorderPolicy.move(items, fromIndex = 0, toIndex = 5))
        assertSame(items, ReorderPolicy.move(items, fromIndex = -1, toIndex = 1))
    }

    @Test
    fun `indexDelta rounds to the nearest row`() {
        assertEquals(0, ReorderPolicy.indexDelta(dragOffsetPx = 20f, rowHeightPx = 100f))
        assertEquals(1, ReorderPolicy.indexDelta(dragOffsetPx = 60f, rowHeightPx = 100f))
        assertEquals(-1, ReorderPolicy.indexDelta(dragOffsetPx = -51f, rowHeightPx = 100f))
    }

    @Test
    fun `indexDelta is zero for a non-positive row height`() {
        assertEquals(0, ReorderPolicy.indexDelta(dragOffsetPx = 200f, rowHeightPx = 0f))
        assertEquals(0, ReorderPolicy.indexDelta(dragOffsetPx = 200f, rowHeightPx = -10f))
    }
}
