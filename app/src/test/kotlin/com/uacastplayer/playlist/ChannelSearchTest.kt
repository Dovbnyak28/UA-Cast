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

    // The cases below pin the matcher that replaced "normalize the channel name into a String, then
    // call contains" - see ChannelSearch.containsNormalized. Each is something that version got
    // right for free and a character-walking matcher can plausibly get wrong.

    @Test
    fun `matches a name whose only difference is leading and trailing whitespace`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("   HBO Max   "))), "hbo max")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `collapses tabs and newlines the same way it collapses spaces`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("HBO\t\n  Max"))), "hbo max")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    /** A query space must consume the whole run, not one character of it - otherwise the match
     * restarts mid-run and silently fails. */
    @Test
    fun `a single query space matches a long whitespace run`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Sport          1 HD"))), "sport 1")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `does not match across a word boundary the query does not have`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Sport 1"))), "sport1")
        check(outcome is ChannelSearchOutcome.Matches)
        assertTrue(outcome.results.isEmpty())
    }

    @Test
    fun `matches a substring starting mid-name, not just a prefix`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Ukraine Discovery HD"))), "discovery hd")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    /** The scan tries every source index as a start, so a name where the query almost matches
     * earlier must still match at the later, real position. */
    @Test
    fun `matches after a false start earlier in the name`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Disco Dance Discovery"))), "discovery")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `does not match when the name runs out mid-query`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Disc"))), "discovery")
        check(outcome is ChannelSearchOutcome.Matches)
        assertTrue(outcome.results.isEmpty())
    }

    @Test
    fun `a query with internal whitespace runs is normalized before matching`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("HBO Max"))), "  hbo    MAX ")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `matches mixed-case cyrillic mid-name`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Дитячий  НОВИЙ Канал"))), "новий канал")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }

    @Test
    fun `tvgName matching normalizes whitespace too`() {
        val outcome = ChannelSearch.search(listOf(groupOf(channel("Ch1", tvgName = "  Euro   Sport "))), "euro sport")
        check(outcome is ChannelSearchOutcome.Matches)
        assertEquals(1, outcome.results.size)
    }
}
