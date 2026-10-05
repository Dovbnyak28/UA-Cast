package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Not a precise benchmark - CI hardware varies - but a coarse regression guard for the bug fixed in
 * Block 1 (see docs/PERFORMANCE.md): [M3uParser.parse] + [ChannelGrouper.group] together used to run
 * on the main thread, freezing the UI for as long as this takes. 3000 channels mirrors a real user's
 * playlist size. If this ever creeps into multi-second territory, something regressed from O(n) back
 * to O(n^2) - the 2s budget is intentionally loose so normal CI noise doesn't make this flaky.
 */
class PlaylistParsePerformanceTest {

    private fun playlist(channelCount: Int): String = buildString {
        appendLine("#EXTM3U")
        val groups = listOf("News", "Movies", "Sports", "Kids", "Music")
        repeat(channelCount) { index ->
            val group = groups[index % groups.size]
            appendLine(
                """#EXTINF:-1 tvg-id="ch$index" tvg-logo="http://cdn.example.com/$index.png" """ +
                    """group-title="$group",Channel $index""",
            )
            appendLine("http://example.com/stream$index.m3u8")
        }
    }

    private fun parseAndGroup(playlist: String, expectedChannels: Int): Long = measureTimeMillis {
        val parsed = M3uParser.parse(playlist)
        val grouped = ChannelGrouper.group(parsed.channels)
        assertEquals(expectedChannels, parsed.channels.size)
        assertTrue(grouped.isNotEmpty())
    }

    @Test
    fun `parsing and grouping 3000 channels stays well under a slow-UI budget`() {
        val elapsedMillis = parseAndGroup(playlist(3_000), expectedChannels = 3_000)

        assertTrue(
            "Parsing+grouping 3000 channels took ${elapsedMillis}ms - expected well under 2000ms",
            elapsedMillis < 2000,
        )
    }

    /** Provider-scale guard: catches accidental quadratic parsing/grouping before a 40k-channel
     * playlist reaches a low-end phone. Loose enough for shared CI runners, strict enough that a
     * UI-visible multi-second regression fails loudly. */
    @Test
    fun `parsing and grouping 40000 channels stays within provider scale budget`() {
        val elapsedMillis = parseAndGroup(playlist(40_000), expectedChannels = 40_000)

        assertTrue(
            "Parsing+grouping 40000 channels took ${elapsedMillis}ms - expected under 8000ms",
            elapsedMillis < 8_000,
        )
    }
}
