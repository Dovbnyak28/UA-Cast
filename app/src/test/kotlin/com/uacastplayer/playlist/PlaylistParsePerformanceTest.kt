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

    @Test
    fun `parsing and grouping 3000 channels stays well under a slow-UI budget`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            val groups = listOf("News", "Movies", "Sports", "Kids", "Music")
            repeat(3000) { i ->
                val group = groups[i % groups.size]
                appendLine(
                    """#EXTINF:-1 tvg-id="ch$i" tvg-logo="http://cdn.example.com/$i.png" """ +
                        """group-title="$group",Channel $i""",
                )
                appendLine("http://example.com/stream$i.m3u8")
            }
        }

        val elapsedMillis = measureTimeMillis {
            val parsed = M3uParser.parse(playlist)
            val grouped = ChannelGrouper.group(parsed.channels)
            assertEquals(3000, parsed.channels.size)
            assertTrue(grouped.isNotEmpty())
        }

        assertTrue(
            "Parsing+grouping 3000 channels took ${elapsedMillis}ms - expected well under 2000ms",
            elapsedMillis < 2000,
        )
    }
}
