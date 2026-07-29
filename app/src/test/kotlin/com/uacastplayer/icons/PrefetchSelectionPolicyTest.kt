package com.uacastplayer.icons

import com.uacastplayer.icons.PrefetchSelectionPolicy.PriorityChannels
import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchSelectionPolicyTest {

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
