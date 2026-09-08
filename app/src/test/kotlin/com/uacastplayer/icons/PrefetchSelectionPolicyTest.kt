package com.uacastplayer.icons

import com.uacastplayer.icons.PrefetchSelectionPolicy.PriorityChannels
import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchSelectionPolicyTest {
    @Test fun `empty priority keys never visit the full playlist`() {
        val unvisited = object : AbstractList<M3uChannel>() {
            override val size: Int = 100_000
            override fun get(index: Int): M3uChannel = error("Unnecessary full-playlist scan")
        }
        val firstGroup = List(40) { channel("C$it", tvgId = null) }
        assertEquals(firstGroup, PrefetchSelectionPolicy.select(
            unvisited, PriorityChannels(firstGroupChannels = firstGroup), 300,
        ))
    }

    @Test fun `full favorite budget avoids scanning remaining channels`() {
        val first = channel("First")
        val bounded = object : AbstractList<M3uChannel>() {
            override val size: Int = 100_000
            override fun get(index: Int): M3uChannel = if (index == 0) first else error("Budget already filled")
        }
        assertEquals(listOf(first), PrefetchSelectionPolicy.select(
            bounded, PriorityChannels(favoriteKeys = setOf("First"), lastWatchedKey = "Missing"), 1,
        ))
    }

    @Test fun `cancelled favorite scan propagates cancellation`() {
        var visited = 0
        org.junit.Assert.assertThrows(java.util.concurrent.CancellationException::class.java) {
            PrefetchSelectionPolicy.select(
                List(100_000) { channel("C$it") }, PriorityChannels(favoriteKeys = setOf("Missing")), 300,
                checkCancellation = { if (++visited == 32) throw java.util.concurrent.CancellationException() },
            )
        }
        assertEquals(32, visited)
    }

    private fun channel(name: String, group: String? = "News", tvgId: String? = name) =
        M3uChannel(displayName = name, streamUrl = "http://example.com/$name", tvgId = tvgId, groupTitle = group)

    @Test
    fun `favorites come before the first group's other channels`() {
        val fav = channel("Favorite", group = "Sports")
        val groupMate = channel("GroupMate", group = "News")
        val channels = listOf(groupMate, fav)

        val selected = PrefetchSelectionPolicy.select(
            channels = channels,
            priority = PriorityChannels(favoriteKeys = setOf("Favorite"), firstGroupChannels = listOf(groupMate)),
            limit = 10,
        )

        assertEquals(listOf(fav, groupMate), selected)
    }

    @Test
    fun `total limit is respected across all categories`() {
        val channels = (1..10).map { channel("C$it") }

        val selected = PrefetchSelectionPolicy.select(
            channels = channels,
            priority = PriorityChannels(firstGroupChannels = channels),
            limit = 3,
        )

        assertEquals(3, selected.size)
    }

    @Test
    fun `already-cached channels are skipped and don't consume the limit`() {
        val cached = channel("Cached")
        val notCached = channel("NotCached")

        val selected = PrefetchSelectionPolicy.select(
            channels = listOf(cached, notCached),
            priority = PriorityChannels(favoriteKeys = setOf("Cached", "NotCached")),
            limit = 1,
            isCached = { it.displayName == "Cached" },
        )

        assertEquals(listOf(notCached), selected)
    }

    @Test
    fun `last-watched channel is included even outside favorites and the first group`() {
        val lastWatched = channel("LastWatched", group = "Movies")
        val other = channel("Other", group = "News")

        val selected = PrefetchSelectionPolicy.select(
            channels = listOf(other, lastWatched),
            priority = PriorityChannels(lastWatchedKey = "LastWatched", firstGroupChannels = listOf(other)),
            limit = 10,
        )

        assertTrue(lastWatched in selected)
    }

    @Test
    fun `a channel that is both a favorite and last-watched is only selected once`() {
        val both = channel("Both")

        val selected = PrefetchSelectionPolicy.select(
            channels = listOf(both),
            priority = PriorityChannels(favoriteKeys = setOf("Both"), lastWatchedKey = "Both"),
            limit = 10,
        )

        assertEquals(listOf(both), selected)
    }

    @Test
    fun `empty channel list selects nothing`() {
        val selected = PrefetchSelectionPolicy.select(
            channels = emptyList(),
            priority = PriorityChannels(),
            limit = 300,
        )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `zero limit selects nothing even with favorites present`() {
        val fav = channel("Favorite")

        val selected = PrefetchSelectionPolicy.select(
            channels = listOf(fav),
            priority = PriorityChannels(favoriteKeys = setOf("Favorite")),
            limit = 0,
        )

        assertTrue(selected.isEmpty())
    }
}
