package com.uacastplayer.playlist

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.icons.PrefetchSelectionPolicy
import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Device CPU/allocation diagnostic, not a release Macrobenchmark or end-to-end import test.
 * Synthetic input stays on the instrumentation worker, outside the UI thread and user storage. */
@RunWith(AndroidJUnit4::class)
class PlaylistDeviceAuditInstrumentedTest {
    @Test fun attributeDialectMatchesAndroidLegacyRegex() {
        val legacy = Regex("""([a-zA-Z][\w-]*)=(?:"([^"]*)"|(\S+))""")
        val separators = listOf(" ", "\t", "\u00A0", "\u2003", "\u0085", "\u200B")
        val keys = listOf("tvg-id", "aїtvg-id", "a\u0301tvg-id", "a\u200Ctvg-id", "a𐐀tvg-id")
        for (separator in separators) {
            for (key in keys) {
                val input = "$key=Україна${separator}group-title=España"
                val expected = legacy.findAll(input).map { match ->
                    match.groupValues[1] to (match.groups[2]?.value ?: match.groups[3]?.value).orEmpty()
                }.toList()
                val actual = buildList {
                    M3uAttributeReader(input, 0, input.length, {}).forEach { name, value -> add(name to value) }
                }
                assertEquals(input, expected, actual)
            }
        }
    }

    @Test fun hostileLinesCancelAndDoNotCorruptTheNextParse() {
        for (prefix in listOf("#EXTINF:-1 ", "#EXTM3U ", "#EXTM3U url-tvg=\"")) {
            var probes = 0
            assertThrows(CancellationException::class.java) {
                M3uParser.parse(prefix + "a".repeat(8_192)) {
                    if (++probes == 3) throw CancellationException("synthetic cancellation")
                }
            }
        }
        val parsed = M3uParser.parse(
            "#EXTM3U url-tvg=\"https://example.test/epg\"\n" +
                "#EXTINF:-1 ${"a".repeat(1_048_576)} TVG-ID=right group-title=\"Новини\",Україна, España\n" +
                "https://example.test/live",
        )
        assertEquals("right", parsed.channels.single().tvgId)
        assertEquals("Новини", parsed.channels.single().groupTitle)
        assertEquals("Україна, España", parsed.channels.single().displayName)
        assertEquals(listOf("https://example.test/epg"), parsed.epgUrls)
    }

    @Test fun measureBoundedMalformedAttributeCost() {
        val report = StringBuilder("Bounded malformed EXTINF token; debug ART; no user input\n")
        for (length in listOf(500, 1_000, 2_000, 4_000, 8_000)) {
            val input = "#EXTINF:-1 ${"a".repeat(length)},Name\nhttps://example.test/live"
            repeat(2) { sample ->
                val start = System.nanoTime()
                assertEquals("Name", M3uParser.parse(input).channels.single().displayName)
                report.appendLine("length=$length sample=$sample elapsedNs=${System.nanoTime() - start}")
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = checkNotNull(context.getExternalFilesDir("player-controls-audit"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "malformed-attribute-cost.txt").writeText(report.toString())
    }

    @Test fun measureProviderSizedParseAndGrouping() {
        M3uParser.parse(playlist(500)) // Small warmup, outside measured sections.
        val report = StringBuilder("Debug ART; warmup=500 channels; 3 samples per size; no forced GC\n")
        for (size in listOf(3_000, 10_000, 40_000)) {
            val input = playlist(size)
            report.appendLine("channels=$size inputUtf8Bytes=${input.toByteArray().size}")
            repeat(3) { sample ->
                val allocated = allocatedBytes()
                val gcTime = gcTimeMillis()
                val start = System.nanoTime()
                val parsed = M3uParser.parse(input)
                val groups = ChannelGrouper.group(parsed.channels)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                assertEquals(size, parsed.channels.size)
                assertEquals(size, groups.sumOf { it.channels.size })
                report.appendLine(
                    "sample=$sample parseAndGroupMs=$elapsed " +
                        "processAllocatedBytes=${delta(allocated, allocatedBytes())} " +
                        "processGcTimeMs=${delta(gcTime, gcTimeMillis())}",
                )
            }
        }
        measurePrefetchSelection(report)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = checkNotNull(context.getExternalFilesDir("player-controls-audit"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "playlist-device-measurement.txt").writeText(report.toString())
    }

    private fun allocatedBytes() = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
    private fun gcTimeMillis() = Debug.getRuntimeStat("art.gc.gc-time")?.toLongOrNull()
    private fun delta(before: Long?, after: Long?): String =
        if (before == null || after == null) "unavailable" else (after - before).toString()

    private fun measurePrefetchSelection(report: StringBuilder) {
        val channels = M3uParser.parse(playlist(40_000)).channels.map { it.copy(tvgId = null) }
        val priority = PrefetchSelectionPolicy.PriorityChannels(firstGroupChannels = channels.take(40))
        repeat(3) { sample ->
            val start = System.nanoTime()
            val selected = PrefetchSelectionPolicy.select(channels, priority, limit = 300)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertEquals(40, selected.size)
            report.appendLine("prefetchSelectionWithoutTvgId sample=$sample channels=40000 selected=40 ms=$elapsed")
        }
    }

    private fun playlist(count: Int): String = buildString {
        appendLine("#EXTM3U")
        repeat(count) { index ->
            appendLine(
                "#EXTINF:-1 tvg-id=\"ch$index\" tvg-logo=\"https://example.test/$index.png\" " +
                    "group-title=\"Group ${index / 40}\",Channel $index",
            )
            appendLine("https://example.test/$index.m3u8")
        }
    }
}
