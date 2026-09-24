package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 stores the parsed guide; v1 stored the raw XMLTV document, which every cold start had to
 * re-inflate and re-parse - measured at 53 seconds against a real 44.9MB snapshot on a real device.
 * These cover the round trip, the one-way upgrade path off v1, and the channel-id pooling the
 * format is deliberately shaped around.
 */
class EpgSnapshotCodecTest {

    private val header = EpgSnapshotHeader(sourceFingerprint = "abc123", savedAtEpochMillis = 1_700_000_000_000L)

    private fun dataOf(
        channels: List<EpgChannel>,
        programmes: Map<String, List<EpgProgramme>>,
        truncation: EpgTruncation = EpgTruncation.NONE,
    ) = EpgData(EpgIndex(channels), programmes, truncation)

    private fun roundTrip(data: EpgData): DecodedEpgSnapshot.Parsed {
        val bytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, data, it) }.toByteArray()
        return EpgSnapshotCodec.decode(ByteArrayInputStream(bytes)) as DecodedEpgSnapshot.Parsed
    }

    @Test
    fun `a full guide survives a round trip`() {
        val data = dataOf(
            channels = listOf(
                EpgChannel("ch1", listOf("Перший", "First"), "http://cdn.example.com/1.png"),
                EpgChannel("ch2", listOf("Другий"), null),
            ),
            programmes = mapOf(
                "ch1" to listOf(
                    EpgProgramme("ch1", 1000L, 2000L, "Новини"),
                    EpgProgramme("ch1", 2000L, 3000L, "Погода"),
                ),
                "ch2" to listOf(EpgProgramme("ch2", 1500L, 2500L, "Фільм")),
            ),
        )

        val decoded = roundTrip(data)

        assertEquals(header, decoded.header)
        assertEquals(data.index.channels, decoded.data.index.channels)
        assertEquals(data.programmesByChannelId, decoded.data.programmesByChannelId)
    }

    @Test fun `old snapshots cannot bypass the new per channel alias budget`() {
        val names = (0..100).map { "Name $it" }
        val decoded = roundTrip(dataOf(listOf(EpgChannel("one", names, null)), emptyMap()))
        assertEquals(XmlTvChannelNames.MAX_PER_CHANNEL, decoded.data.index.channels.single().displayNames.size)
        assertTrue(decoded.data.truncation.any)
    }

    /** The layout writes each channel id once per group precisely so this holds: a channel's whole
     * schedule shares one String rather than allocating one per programme, which is what keeps
     * 250,000 rows affordable. */
    @Test
    fun `every programme in a channel shares one channel-id instance`() {
        val programmes = (1..50).map { EpgProgramme("ch1", it * 1000L, it * 1000L + 500, "P$it") }

        val restored = roundTrip(
            dataOf(listOf(EpgChannel("ch1", listOf("One"), null)), mapOf("ch1" to programmes)),
        ).data.programmesByChannelId.getValue("ch1")

        val first = restored.first().channelId
        for (programme in restored) {
            assertSame("channel ids must be pooled, not one String per programme", first, programme.channelId)
        }
    }

    @Test
    fun `truncation flags survive a round trip`() {
        val truncated = EpgTruncation(channelsDropped = true, programmesDropped = true)

        val decoded = roundTrip(dataOf(emptyList(), emptyMap(), truncated))

        assertEquals(truncated, decoded.data.truncation)
        assertTrue(decoded.data.truncation.any)
    }

    @Test
    fun `restoring a guide retains only the requested programme budget and flags truncation`() {
        val source = dataOf(
            channels = listOf(EpgChannel("one", listOf("One"), null), EpgChannel("two", listOf("Two"), null)),
            programmes = mapOf(
                "one" to listOf(
                    EpgProgramme("one", 1L, 2L, "A"),
                    EpgProgramme("one", 3L, 4L, "B"),
                ),
                "two" to listOf(
                    EpgProgramme("two", 5L, 6L, "C"),
                    EpgProgramme("two", 7L, 8L, "D"),
                ),
            ),
        )
        val bytes = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, source, it) }.toByteArray()

        val decoded = EpgSnapshotCodec.decode(ByteArrayInputStream(bytes), maxProgrammes = 3)
            as DecodedEpgSnapshot.Parsed

        assertEquals(3, decoded.data.programmesByChannelId.values.sumOf(List<EpgProgramme>::size))
        assertEquals(listOf("A", "B"), decoded.data.programmesByChannelId.getValue("one").map(EpgProgramme::title))
        assertEquals(listOf("C"), decoded.data.programmesByChannelId.getValue("two").map(EpgProgramme::title))
        assertTrue(decoded.data.truncation.programmesDropped)
    }

    @Test
    fun `restoring an old oversized title retains only parser-sized text`() {
        val longTitle = "x".repeat(XmlTvParser.MAX_TEXT_LENGTH + 200)
        val decoded = roundTrip(
            dataOf(
                channels = listOf(EpgChannel("one", listOf("One"), null)),
                programmes = mapOf("one" to listOf(EpgProgramme("one", 1L, 2L, longTitle))),
            ),
        )

        val restoredTitle = decoded.data.programmesByChannelId.getValue("one").single().title
        assertEquals(XmlTvParser.MAX_TEXT_LENGTH, restoredTitle.length)
    }

    @Test
    fun `restoring an icon beyond parser attribute budget drops only the icon and reports truncation`() {
        val decoded = roundTrip(
            dataOf(
                channels = listOf(
                    EpgChannel(
                        "one",
                        listOf("One"),
                        "https://example.test/${"x".repeat(XmlTvParser.MAX_ATTRIBUTE_LENGTH)}",
                    ),
                ),
                programmes = emptyMap(),
            ),
        )

        assertEquals(null, decoded.data.index.channels.single().iconUrl)
        assertTrue(decoded.data.truncation.channelsDropped)
    }

    @Test
    fun `programme-only groups use the parser channel-id pool ceiling`() {
        val groups = (0..XmlTvParser.MAX_CHANNELS).associate { index ->
            "orphan-$index" to emptyList<EpgProgramme>()
        }

        val decoded = roundTrip(dataOf(emptyList(), groups))

        assertEquals(groups.size, decoded.data.programmesByChannelId.size)
    }

    @Test
    fun `snapshot encoding rejects programme groups beyond parser id pool`() {
        val groups = (0..XmlTvParser.MAX_CHANNEL_ID_POOL).associate { index ->
            "orphan-$index" to emptyList<EpgProgramme>()
        }
        val snapshot = dataOf(emptyList(), groups)

        try {
            EpgSnapshotCodec.encode(header, snapshot, ByteArrayOutputStream())
            throw AssertionError("encoder must reject a group count it cannot restore")
        } catch (_: java.io.IOException) {
            // Reject before persisting a snapshot that this build cannot read back.
        }
    }

    @Test
    fun `an empty guide round trips without special-casing`() {
        val decoded = roundTrip(dataOf(emptyList(), emptyMap()))

        assertTrue(decoded.data.index.channels.isEmpty())
        assertTrue(decoded.data.programmesByChannelId.isEmpty())
        assertEquals(EpgTruncation.NONE, decoded.data.truncation)
    }

    @Test
    fun `a channel with no icon and no display names round trips`() {
        val decoded = roundTrip(dataOf(listOf(EpgChannel("bare", emptyList(), null)), emptyMap()))

        val channel = decoded.data.index.channels.single()
        assertEquals("bare", channel.id)
        assertTrue(channel.displayNames.isEmpty())
        assertNull(channel.iconUrl)
    }

    /**
     * A snapshot written by the previous release still has to be readable, or upgrading would throw
     * away a guide the user already has and leave them with none until a fresh download finished -
     * offline or not.
     */
    @Test
    fun `a v1 snapshot is returned as a document stream, not discarded`() {
        val documentBytes = "<tv><channel id=\"ch1\"/></tv>".toByteArray()
        val v1 = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(1)
                writeUTF(header.sourceFingerprint)
                writeLong(header.savedAtEpochMillis)
                writeLong(documentBytes.size.toLong())
                flush()
            }
            write(documentBytes)
        }.toByteArray()

        val decoded = EpgSnapshotCodec.decode(ByteArrayInputStream(v1))

        val document = decoded as DecodedEpgSnapshot.Document
        assertEquals(header, document.header)
        assertEquals(String(documentBytes), document.documentStream.readBytes().decodeToString())
    }

    @Test
    fun `decoding an unknown format version returns null`() {
        val future = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply { writeInt(999); writeUTF("fp"); flush() }
        }.toByteArray()

        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(future)))
    }

    @Test
    fun `decoding truncated data returns null instead of throwing`() {
        val data = dataOf(
            listOf(EpgChannel("ch1", listOf("One"), null)),
            mapOf("ch1" to listOf(EpgProgramme("ch1", 1, 2, "T"))),
        )
        val full = ByteArrayOutputStream().also { EpgSnapshotCodec.encode(header, data, it) }.toByteArray()

        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(full.copyOf(full.size / 2))))
    }

    @Test
    fun `decoding an empty stream returns null`() {
        assertNull(EpgSnapshotCodec.decode(ByteArrayInputStream(ByteArray(0))))
    }
}
