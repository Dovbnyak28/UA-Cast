package com.uacastplayer.performance

import com.uacastplayer.playlist.ChannelGrouper
import com.uacastplayer.playlist.ChannelSearch
import com.uacastplayer.playlist.M3uParser
import java.util.Locale
import org.junit.Test

/**
 * Measures what the playlist pipeline actually costs, at the sizes a real IPTV provider produces.
 *
 * This is a JVM measurement, not a device one: it times pure Kotlin over synthetic input on the
 * machine running the build, so the absolute numbers are optimistic compared to a phone (desktop
 * JIT, no other load, no dalvik). What it is good for is the shape of the curve and the relative
 * cost of a change - if 100k channels parse in linear time here, they parse in linear time there
 * too, and a change that halves the work halves it on both.
 *
 * It prints rather than asserts. A timing threshold in a unit test on shared CI hardware is a
 * flaky test, not a guard; the numbers are for reading, and the regression guard for behaviour is
 * the rest of the suite.
 */
class PlaylistBenchmark {

    private val sizes = listOf(100, 1_000, 10_000, 50_000, 100_000)

    /** A playlist shaped like a real one: quoted attributes, a logo url, a group per 40 channels,
     * names with spaces and mixed case, so the parser and the search both do their real work. */
    private fun playlist(channelCount: Int): String = buildString(channelCount * 180) {
        append("#EXTM3U url-tvg=\"https://example.com/epg.xml.gz\"\n")
        for (i in 0 until channelCount) {
            append("#EXTINF:-1 tvg-id=\"ch")
            append(i)
            append("\" tvg-name=\"Channel Name ")
            append(i)
            append("\" tvg-logo=\"https://example.com/logos/")
            append(i)
            append(".png\" group-title=\"Group ")
            append(i / 40)
            append("\",Channel Name ")
            append(i)
            append("\nhttps://example.com/stream/")
            append(i)
            append(".m3u8\n")
        }
    }

    private fun <T> measure(label: String, warmups: Int = 1, block: () -> T): T {
        repeat(warmups) { block() }

        val runtime = Runtime.getRuntime()
        // A measurement needs a known starting point, and there is no other way to ask for one.
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()
        Thread.sleep(SETTLE_MILLIS)
        val beforeBytes = runtime.totalMemory() - runtime.freeMemory()

        val startNanos = System.nanoTime()
        val result = block()
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0

        val afterBytes = runtime.totalMemory() - runtime.freeMemory()
        val retainedMb = (afterBytes - beforeBytes) / (1024.0 * 1024.0)

        println(String.format(Locale.ROOT, "%-44s %9.1f ms   %+8.1f MB", label, elapsedMillis, retainedMb))
        return result
    }

    @Test
    fun m3uParsing() {
        println("\n=== M3U parse ===")
        for (size in sizes) {
            val text = playlist(size)
            println(String.format(Locale.ROOT, "input: %,d channels, %,d KB", size, text.length / 1024))
            val result = measure("  parse $size") { M3uParser.parse(text) }
            check(result.channels.size == size) { "expected $size channels, got ${result.channels.size}" }
        }
    }

    @Test
    fun grouping() {
        println("\n=== grouping ===")
        for (size in sizes) {
            val channels = M3uParser.parse(playlist(size)).channels
            measure("  group $size") { ChannelGrouper.group(channels) }
        }
    }

    @Test
    fun search() {
        println("\n=== search (whole playlist, per keystroke) ===")
        for (size in sizes) {
            val groups = ChannelGrouper.group(M3uParser.parse(playlist(size)).channels)

            // A query that matches almost everything: the worst case, because the result list fills
            // up and every channel is examined.
            measure("  search '$BROAD_QUERY' in $size", warmups = 2) { ChannelSearch.search(groups, BROAD_QUERY) }

            // A query that matches nothing: also a worst case, and the more common one while a user
            // is still typing - nothing short-circuits, every channel is scanned to the end.
            measure("  search '$MISS_QUERY' in $size", warmups = 2) { ChannelSearch.search(groups, MISS_QUERY) }
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 60L
        const val BROAD_QUERY = "channel"
        const val MISS_QUERY = "zzqqxx"
    }
}
