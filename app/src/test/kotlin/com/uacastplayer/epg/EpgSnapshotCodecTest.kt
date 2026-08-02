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
