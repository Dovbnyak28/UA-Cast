package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

private fun group(rawTitle: String): GroupedChannels = GroupedChannels(ChannelGroup.Custom(rawTitle), emptyList())

class GroupOrderPolicyTest {

    @Test
    fun `with no pins or hides, groups keep their original order`() {
        val groups = listOf(group("A"), group("B"), group("C"))
        val ordered = GroupOrderPolicy.order(groups, pinnedKeys = emptySet(), hiddenKeys = emptySet())
        assertEquals(groups, ordered)
    }

    @Test
    fun `pinned groups move to the front, in their original relative order`() {
        val a = group("A")
        val b = group("B")
        val c = group("C")
        val ordered = GroupOrderPolicy.order(
            listOf(a, b, c),
            pinnedKeys = setOf(groupDisplayKey(c.group), groupDisplayKey(a.group)),
            hiddenKeys = emptySet(),
        )
        assertEquals(listOf(a, c, b), ordered)
    }

    @Test
    fun `hidden groups are excluded entirely`() {
        val a = group("A")
        val b = group("B")
        val ordered = GroupOrderPolicy.order(
            listOf(a, b),
            pinnedKeys = emptySet(),
            hiddenKeys = setOf(groupDisplayKey(a.group)),
        )
        assertEquals(listOf(b), ordered)
    }

    @Test
    fun `a group that is both pinned and hidden is excluded, not pinned`() {
        val a = group("A")
        val b = group("B")
        val key = groupDisplayKey(a.group)
        val ordered = GroupOrderPolicy.order(listOf(a, b), pinnedKeys = setOf(key), hiddenKeys = setOf(key))
        assertEquals(listOf(b), ordered)
    }

    @Test
    fun `pinned and hidden combine correctly`() {
        val a = group("A")
        val b = group("B")
        val c = group("C")
        val ordered = GroupOrderPolicy.order(
            listOf(a, b, c),
            pinnedKeys = setOf(groupDisplayKey(c.group)),
            hiddenKeys = setOf(groupDisplayKey(a.group)),
        )
        assertEquals(listOf(c, b), ordered)
    }
}
