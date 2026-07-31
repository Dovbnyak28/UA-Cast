package com.uacastplayer.home

import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeContentPolicyTest {

    private fun channel(name: String, tvgId: String? = null) =
        M3uChannel(displayName = name, streamUrl = "http://example.com/$name", tvgId = tvgId)

    private fun favorite(name: String) = FavoriteChannel(
        key = name,
        displayName = name,
        streamUrl = "http://example.com/$name",
        tvgId = null,
        groupTitle = null,
    )

    @Test
    fun `resolves the continue-watching channel by its favorite key`() {
        val channel = channel("BBC", tvgId = "bbc")
        val content = HomeContentPolicy.resolve(
            lastWatchedChannelKey = FavoriteKey.of(channel),
            channels = listOf(channel("CNN", tvgId = "cnn"), channel),
            favorites = emptyList(),
        )
        assertEquals(channel, content.continueWatching)
    }

    @Test
    fun `no last-watched key means no continue-watching card`() {
        val content = HomeContentPolicy.resolve(
            lastWatchedChannelKey = null,
            channels = listOf(channel("CNN")),
            favorites = emptyList(),
        )
        assertNull(content.continueWatching)
    }

    @Test
    fun `a key that matches nothing in the current playlist drops the card instead of erroring`() {
        val content = HomeContentPolicy.resolve(
            lastWatchedChannelKey = "some-stale-key",
            channels = listOf(channel("CNN")),
            favorites = emptyList(),
        )
        assertNull(content.continueWatching)
    }

    @Test
    fun `an empty playlist never shows a continue-watching card`() {
        val content = HomeContentPolicy.resolve(
            lastWatchedChannelKey = "any-key",
            channels = emptyList(),
            favorites = emptyList(),
        )
        assertNull(content.continueWatching)
    }

    @Test
    fun `favorites pass through untouched when under the cap`() {
        val favorites = listOf(favorite("A"), favorite("B"))
        val content = HomeContentPolicy.resolve(null, emptyList(), favorites)
        assertEquals(favorites, content.favorites)
    }

    @Test
    fun `favorites are capped without reordering`() {
        val favorites = (1..15).map { favorite("Fav$it") }
        val content = HomeContentPolicy.resolve(null, emptyList(), favorites)
        assertEquals(HomeContentPolicy.MAX_FAVORITES_SHOWN, content.favorites.size)
        assertEquals(favorites.take(HomeContentPolicy.MAX_FAVORITES_SHOWN), content.favorites)
    }

    @Test
    fun `no favorites yields an empty list, not a placeholder`() {
        val content = HomeContentPolicy.resolve(null, emptyList(), emptyList())
        assertEquals(emptyList<FavoriteChannel>(), content.favorites)
    }
}
