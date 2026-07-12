package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelGrouperTest {

    private fun channel(name: String, group: String? = null) =
        M3uChannel(displayName = name, streamUrl = "http://example.com/$name", groupTitle = group)

    @Test
    fun `preserves channel order within a group`() {
        val channels = listOf(
            channel("B", "News"),
            channel("A", "News"),
        )
        val result = ChannelGrouper.group(channels)
        assertEquals(listOf("B", "A"), result.single().channels.map { it.displayName })
    }

    @Test
    fun `known groups appear in the curated priority order regardless of input order`() {
        val channels = listOf(
            channel("Sport1", "Sport"),
            channel("News1", "News"),
            channel("Movie1", "Movies"),
        )
        val result = ChannelGrouper.group(channels)
        val keys = result.map { (it.group as ChannelGroup.Known).key }
        assertEquals(listOf(ChannelGroup.KEY_NEWS, ChannelGroup.KEY_MOVIES, ChannelGroup.KEY_SPORTS), keys)
    }

    @Test
    fun `custom groups are sorted alphabetically case-insensitively`() {
        val channels = listOf(
            channel("C1", "zeta group"),
            channel("C2", "Alpha Group"),
        )
        val result = ChannelGrouper.group(channels)
        val titles = result.map { (it.group as ChannelGroup.Custom).rawTitle }
        assertEquals(listOf("Alpha Group", "zeta group"), titles)
    }

    @Test
    fun `ungrouped channels always sort last`() {
        val channels = listOf(
            channel("NoGroup", null),
            channel("Custom", "Weird Group"),
            channel("News1", "News"),
        )
        val result = ChannelGrouper.group(channels)
        assertEquals(ChannelGroup.Ungrouped, result.last().group)
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<GroupedChannels>(), ChannelGrouper.group(emptyList()))
    }
}
