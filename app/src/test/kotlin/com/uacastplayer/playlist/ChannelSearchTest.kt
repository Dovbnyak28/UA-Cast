package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchTest {

    private fun channel(name: String, tvgName: String? = null) =
        M3uChannel(displayName = name, streamUrl = "http://example.com/$name", tvgName = tvgName)

    private fun groupOf(vararg channels: M3uChannel) = GroupedChannels(ChannelGroup.Custom("Group"), channels.toList())

    @Test
    fun `matches by displayName case-insensitively`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("BBC News"))), "bbc")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(listOf("BBC News"), outcome.results.map { it.channel.displayName })
    }

    @Test
    fun `matches cyrillic queries case-insensitively`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Новини 24"))), "НОВИНИ")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `matches latin queries without being confused by cyrillic entries in the same list`() {
        val outcome = ChannelSearch.search(
            listOf(groupOf(channel("CNN International"), channel("СТБ"))),
            "cnn",
        )
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `falls back to tvgName when displayName does not match`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Ch1", tvgName = "Discovery Channel"))), "discovery")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `normalizes internal whitespace so formatting differences still match`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("HBO   Max"))), "hbo max")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `a blank query returns no results rather than the whole playlist`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Anything"))), "   ")
        check(outcome is ChannelSearchOutcome.Matches)
        assertTrue(outcome.results.isEmpty())
    }

    @Test
    fun `results carry the group they came from and preserve playlist order across groups`() {
        val groupA = GroupedChannels(ChannelGroup.Custom("A"), listOf(channel("Alpha One")))
        val groupB = GroupedChannels(ChannelGroup.Custom("B"), listOf(channel("Alpha Two")))
        val outcome = ChannelSearch.search(listOf(groupA, groupB), "alpha")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(listOf("A", "B"), outcome.results.map { (it.group as ChannelGroup.Custom).rawTitle })
        assertEquals(listOf("Alpha One", "Alpha Two"), outcome.results.map { it.channel.displayName })
    }

    @Test
    fun `caps results at MAX_RESULTS and reports the search as too broad`() {
        val channels = (1..250).map { channel("Match $it") }
        val outcome = ChannelSearch.search(listOf(groupOf(*channels.toTypedArray())), "match")
        check(outcome is ChannelSearchOutcome.TooBroad)
        assertEquals(ChannelSearch.MAX_RESULTS, outcome.results.size)
        assertEquals("Match 1", outcome.results.first().channel.displayName)
    }

    @Test
    fun `a match count exactly at the cap is not reported as too broad`() {
        val channels = (1..ChannelSearch.MAX_RESULTS).map { channel("Match $it") }
        val outcome = ChannelSearch.search(listOf(groupOf(*channels.toTypedArray())), "match")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(ChannelSearch.MAX_RESULTS, outcome.results.size)
    }
}
