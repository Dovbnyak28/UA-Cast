package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reason [EpgSnapshotCodec] moved to storing the parsed guide instead of the XMLTV document it
 * came from.
 *
 * The old format meant every cold start re-inflated and re-parsed the document to rebuild data the
 * app had already had: measured on a real device against a real feed, restoring a 44.9MB snapshot
 * took **53 seconds** of background CPU to yield 676 channels and 250,000 programmes.
 *
 * Not a precise benchmark - hardware varies, and this runs at a fraction of the real feed's size to
 * stay quick - but a coarse guard that restoring the snapshot stays far cheaper than parsing the
 * document.
 * Both inputs contain the same schedule, and both measured paths produce query-ready [EpgData]:
 * the XML path includes production guards, retention, grouping, sorting and index construction.
 * Comparing binary restore to the raw SAX parser would omit work the app must do after parsing.
 *
 * It compares the **fastest** of several runs on each side, not the total of them. A loose ratio was
 * supposed to absorb CI noise on its own and did not: summing five timed windows means one window
 * losing its core to another Gradle task inflates that side's total, and this failed at 130ms vs
 * 194ms while running alongside six other tasks - then passed three times in a row alone. A stolen
 * timeslice can only ever make a run slower, so the minimum is the closest thing to the uncontended
 * cost that a wall-clock measurement can offer, and contention has to hit *every* iteration of one
 * side to move it.
 *
 * There is deliberately **no assertion here about file size.** The obvious one - "the parsed
 * snapshot is smaller than the gzipped document" - measures the fixture rather than the format:
 * synthetic titles are near-identical, so gzip compresses the generated XML especially well,
 * telling you little about a real feed. What makes the real file shrink is that the parsed form
 * drops `<desc>`, which dominates a genuine XMLTV document and which no synthetic fixture here
 * reproduces honestly. The size win is therefore measured end to end on a device instead - see
 * docs/PERFORMANCE.md.
 */
class EpgSnapshotSizeTest {

    private companion object {
        const val CHANNELS = 200
        const val PROGRAMMES_PER_CHANNEL = 60
        // Whole-millisecond timing once rounded a 5ms/10ms run onto the strict 50% boundary.
        // This still requires a meaningful speedup while tolerating noisy shared CI runners.
        const val PARSE_BUDGET_RATIO = 0.75
        const val TIMED_RUNS = 5
        const val FIXTURE_HEAP_BYTES = 256L * 1024 * 1024
        val XMLTV_TIMESTAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss Z", Locale.ROOT)
            .withZone(ZoneOffset.UTC)
    }

    /** Wall-clock cost of the quickest of [TIMED_RUNS] runs - see the class doc for why the minimum
     * rather than the total. */
    private fun fastestOf(block: () -> Unit): Long =
        (1..TIMED_RUNS).minOf { measureNanoTime(block) }

    private val header = EpgSnapshotHeader("fp", 1_700_000_000_000L)

    private fun sampleData(): EpgData {
        val channels = (0 until CHANNELS).map {
            EpgChannel("ch$it", listOf("Канал $it"), "http://cdn.example.com/$it.png")
        }
        val programmes = channels.associate { channel ->
            channel.id to (0 until PROGRAMMES_PER_CHANNEL).map { slot ->
                EpgProgramme(
                    channelId = channel.id,
                    startMillis = slot * 1_800_000L,
                    stopMillis = slot * 1_800_000L + 1_800_000L,
                    title = "Передача $slot на каналі ${channel.id}",
                )
            }
        }
        return EpgData(EpgIndex(channels), programmes)
    }

    /** The equivalent XMLTV document, gzipped exactly as a feed arrives and as v1 stored it. */
    private fun equivalentGzippedXmltv(data: EpgData): ByteArray {
        val xml = buildString {
            append("<tv>")
            for (channel in data.index.channels) {
                append("<channel id=\"${channel.id}\"><display-name>${channel.displayNames.first()}</display-name>")
                append("<icon src=\"${channel.iconUrl}\"/></channel>")
            }
            for ((channelId, programmes) in data.programmesByChannelId) {
                for (programme in programmes) {
                    val start = XMLTV_TIMESTAMP.format(Instant.ofEpochMilli(programme.startMillis))
                    val stop = XMLTV_TIMESTAMP.format(Instant.ofEpochMilli(programme.stopMillis))
                    append("<programme start=\"$start\" stop=\"$stop\" ")
                    append("channel=\"$channelId\"><title>${programme.title}</title></programme>")
                }
            }
            append("</tv>")
        }
        return ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(xml.toByteArray()) }
        }.toByteArray()
    }

    @Test
    fun snapshotAndXmlFixturesContainTheSameGuide() {
        val data = sampleData()
        val parsedBytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, data, it) }.toByteArray()
        val decoded = EpgSnapshotCodec.decode(ByteArrayInputStream(parsedBytes)) as DecodedEpgSnapshot.Parsed
        val xml = parseDocument(equivalentGzippedXmltv(data))
        for (restored in listOf(decoded.data, xml)) {
            assertEquals(data.index.channels, restored.index.channels)
            assertEquals(data.truncation, restored.truncation)
            assertTrue(
                "Both fixtures must preserve every programme and its actual times",
                data.programmesByChannelId == restored.programmesByChannelId,
            )
        }
    }

    private fun parseDocument(bytes: ByteArray): EpgData = EpgDocumentPipeline.parse(
        rawInput = ByteArrayInputStream(bytes),
        nowMillis = 0L,
        zoneId = ZoneOffset.UTC,
        maxHeapBytes = FIXTURE_HEAP_BYTES,
    )

    @Test
    fun `decoding the parsed snapshot is far cheaper than parsing the document`() {
        val data = sampleData()
        val parsedBytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, data, it) }.toByteArray()
        val documentBytes = equivalentGzippedXmltv(data)

        // Warm the JIT on both paths first, or the one that runs second wins on that alone.
        repeat(3) {
            EpgSnapshotCodec.decode(ByteArrayInputStream(parsedBytes))
            parseDocument(documentBytes)
        }

        val decodeNanos = fastestOf { EpgSnapshotCodec.decode(ByteArrayInputStream(parsedBytes)) }
        val parseNanos = fastestOf { parseDocument(documentBytes) }

        assertTrue(
            "decoding took ${decodeNanos}ns vs ${parseNanos}ns to parse the document - " +
                "expected under ${(parseNanos * PARSE_BUDGET_RATIO).toLong()}ns",
            decodeNanos < parseNanos * PARSE_BUDGET_RATIO,
        )
    }
}
