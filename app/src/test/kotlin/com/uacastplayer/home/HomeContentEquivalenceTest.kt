package com.uacastplayer.home

import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
import kotlin.random.Random
import org.junit.Assert.assertSame
import org.junit.Test

class HomeContentEquivalenceTest {
    @Test fun `last watched resolution agrees with persisted keys across mixed identifiers`() {
        val random = Random(719)
        val names = listOf("", "News", "News:HD", "Новини", "Ñandú", " BBC ", "Інтер", "news")
        val ids = listOf(null, "", " ", "\u00a0", "id", "News", "id:with:colon")
        repeat(1_000) {
            val channels = List(20) { index ->
                M3uChannel(names.random(random), "https://unused.example.test/$index", tvgId = ids.random(random))
            }
            val key = when (random.nextInt(4)) {
                0 -> null
                1 -> FavoriteKey.of(channels.random(random))
                2 -> "News:invalid-hash"
                else -> "missing"
            }
            val expected = key?.let { value -> channels.firstOrNull { FavoriteKey.of(it) == value } }
            assertSame(expected, HomeContentPolicy.resolve(key, channels, emptyList()).continueWatching)
        }
    }

    @Test fun `duplicate stable identifiers retain the first playlist entry`() {
        val first = M3uChannel("First", "https://unused.example.test/first", tvgId = "shared")
        val second = first.copy(displayName = "Second", streamUrl = "https://unused.example.test/second")
        assertSame(first, HomeContentPolicy.resolve("shared", listOf(first, second), emptyList()).continueWatching)
    }

    @Test fun `fallback lookalike identifiers retain existing first match semantics`() {
        val fallback = M3uChannel("Name:HD", "https://unused.example.test/live")
        val key = FavoriteKey.of(fallback)
        val explicit = fallback.copy(displayName = "Different", tvgId = key)
        assertSame(explicit, HomeContentPolicy.resolve(key, listOf(explicit, fallback), emptyList()).continueWatching)
        assertSame(fallback, HomeContentPolicy.resolve(key, listOf(fallback, explicit), emptyList()).continueWatching)
    }

    @Test fun `a shared URL does not make different fallback names interchangeable`() {
        val first = M3uChannel("News", "https://unused.example.test/live")
        val second = first.copy(displayName = "News:HD")
        assertSame(second, HomeContentPolicy.resolve(
            FavoriteKey.of(second), listOf(first, second), emptyList(),
        ).continueWatching)
    }
}
