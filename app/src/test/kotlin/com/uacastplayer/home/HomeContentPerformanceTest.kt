package com.uacastplayer.home

import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.icons.PrefetchSelectionPolicy
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testsupport.JvmAllocations
import kotlin.system.measureNanoTime
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentPerformanceTest {
    @Test fun `last watched lookup does not allocate discarded keys for unrelated names`() {
        val channels = channels()
        val key = FavoriteKey.of(channels.last())
        repeat(3) { HomeContentPolicy.resolve(key, channels, emptyList()) }
        val before = JvmAllocations.currentThreadBytes()
        val content = HomeContentPolicy.resolve(key, channels, emptyList())
        val allocated = JvmAllocations.currentThreadBytes() - before
        println("Home last-watched 100k allocatedBytes=$allocated")
        assertSame(channels.last(), content.continueWatching)
        assertTrue("Lookup allocated $allocated bytes of discarded keys", allocated < 1_000_000)
    }

    @Test fun `records last watched and prefetch lookup medians on the same playlist`() {
        val channels = channels()
        val key = FavoriteKey.of(channels.last())
        recordMedian("home-100k-last-match") {
            assertSame(channels.last(), HomeContentPolicy.resolve(key, channels, emptyList()).continueWatching)
        }
        recordMedian("home-100k-stale-key") {
            assertSame(null, HomeContentPolicy.resolve("missing", channels, emptyList()).continueWatching)
        }
        recordMedian("prefetch-100k-last-match") {
            val selected = PrefetchSelectionPolicy.select(
                channels, PrefetchSelectionPolicy.PriorityChannels(lastWatchedKey = key), limit = 1,
            )
            assertSame(channels.last(), selected.single())
        }
    }

    private fun recordMedian(label: String, block: () -> Unit) {
        repeat(3) { block() }
        val times = List(7) { measureNanoTime(block) }.sorted()
        println("Home benchmark $label medianNs=${times[times.size / 2]}")
    }

    private fun channels() = List(100_000) { index ->
        M3uChannel("Channel $index", "https://unused.example.test/live/$index")
    }
}
