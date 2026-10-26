package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class ChannelSearchPerformanceTest {
    @Test fun `repeated prefixes stay within a linear scan work budget`() {
        val channels = List(2) { channel("a".repeat(8_192) + "c", it) }
        var probes = 0
        val result = ChannelSearch.search(groups(channels), "a".repeat(2_048) + "b") { probes++ }
        assertEquals(emptyList<ChannelSearchResult>(), (result as ChannelSearchOutcome.Matches).results)
        assertTrue("Repeated prefixes caused $probes cancellation checkpoints; expected linear work", probes <= 64)
        println("ChannelSearch repeated-prefix scan checkpoints=$probes")
    }

    /** Same data, warmup and medians before/after; timings are observations, not a CI speedup assertion. */
    @Test fun `records ordinary and adversarial search timings with bounded outputs`() {
        val ordinary = groups(List(100_000) { channel("Channel $it Новини HD", it) })
        val adversarial = groups(List(16) { channel("a".repeat(8_192) + "c", it) })
        recordMedian("ordinary-100k-miss", ordinary, "unavailable")
        recordMedian("ordinary-100k-late-match", ordinary, "channel 99999")
        recordMedian("ordinary-100k-capped", ordinary, "новини")
        recordMedian("ordinary-100k-long-query", ordinary, "a".repeat(4_096) + "b")
        recordMedian("adversarial-prefixes", adversarial, "a".repeat(2_048) + "b")
    }

    private fun recordMedian(label: String, groups: List<GroupedChannels>, query: String) {
        repeat(3) { ChannelSearch.search(groups, query) }
        val times = List(7) {
            measureNanoTime {
                when (val result = ChannelSearch.search(groups, query)) {
                    is ChannelSearchOutcome.Matches -> assertTrue(result.results.size <= ChannelSearch.MAX_RESULTS)
                    is ChannelSearchOutcome.TooBroad -> assertEquals(ChannelSearch.MAX_RESULTS, result.results.size)
                }
            }
        }.sorted()
        println("ChannelSearch benchmark $label medianNs=${times[times.size / 2]}")
    }

    private fun channel(name: String, index: Int) = M3uChannel(name, "https://unused.example.test/$index")

    private fun groups(channels: List<M3uChannel>) = listOf(GroupedChannels(ChannelGroup.Custom("Test"), channels))
}
