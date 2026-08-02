package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.system.measureTimeMillis
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
 * document. The margin is deliberately loose so ordinary CI noise cannot make it flaky.
 *
 * There is deliberately **no assertion here about file size.** The obvious one - "the parsed
 * snapshot is smaller than the gzipped document" - measures the fixture rather than the format:
 * synthetic titles are near-identical, so gzip crushes the generated XML about eighteen-fold
 * (41KB against 737KB for the binary) and the test fails while telling you nothing true about a
 * real feed. What makes the real file shrink is that the parsed form drops `<desc>`, which
 * dominates a genuine XMLTV document and which no synthetic fixture here reproduces honestly. The
 * size win is therefore measured end to end on a device instead - see docs/PERFORMANCE.md.
 */
class EpgSnapshotSizeTest {

    private companion object {
        const val CHANNELS = 200
        const val PROGRAMMES_PER_CHANNEL = 60
        const val PARSE_BUDGET_RATIO = 0.5
    }

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
                    append("<programme start=\"20240101000000 +0000\" stop=\"20240101003000 +0000\" ")
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
    fun `decoding the parsed snapshot is far cheaper than parsing the document`() {
        val data = sampleData()
        val parsedBytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, data, it) }.toByteArray()
        val documentBytes = equivalentGzippedXmltv(data)

        // Warm the JIT on both paths first, or the one that runs second wins on that alone.
        repeat(3) {
            EpgSnapshotCodec.decode(ByteArrayInputStream(parsedBytes))
            java.util.zip.GZIPInputStream(ByteArrayInputStream(documentBytes)).use(XmlTvParser::parse)
        }

        val decodeMillis = measureTimeMillis {
            repeat(5) { EpgSnapshotCodec.decode(ByteArrayInputStream(parsedBytes)) }
        }
        val parseMillis = measureTimeMillis {
            repeat(5) { java.util.zip.GZIPInputStream(ByteArrayInputStream(documentBytes)).use(XmlTvParser::parse) }
        }

        assertTrue(
            "decoding took ${decodeMillis}ms vs ${parseMillis}ms to parse the document - " +
                "expected well under ${(parseMillis * PARSE_BUDGET_RATIO).toInt()}ms",
            decodeMillis < parseMillis * PARSE_BUDGET_RATIO,
        )
    }
}
