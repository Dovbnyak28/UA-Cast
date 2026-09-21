package com.uacastplayer.playlist

import java.util.Locale
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

    /**
     * The list a viewer scrolls to find their folder, ordered by an alphabet that is not theirs.
     * `rawTitle.lowercase()` compares UTF-16 code units: Ґ is U+0490, past я, and Є/І/Ї are
     * U+0404-0407, in a block that also lands past я. The test above was the only cover this sort
     * had, and it used English words.
     */
    @Test
    fun `custom groups follow the Ukrainian alphabet, not UTF-16 order`() {
        val channels = listOf(
            channel("C1", "Ялта"),
            channel("C2", "Ґазда"),
            channel("C3", "Інтер"),
            channel("C4", "Атлант"),
            channel("C5", "Єдині"),
        )

        val result = ChannelGrouper.group(channels, Locale.forLanguageTag("uk"))

        assertEquals(
            listOf("Атлант", "Ґазда", "Єдині", "Інтер", "Ялта"),
            result.map { (it.group as ChannelGroup.Custom).rawTitle },
        )
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
