package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a damaged snapshot must do: be refused, not be believed.
 *
 * [com.uacastplayer.data.epg.EpgSnapshotStore.open] documents the contract in its own words - a
 * snapshot that does not decode means "parse from the network instead", and "escaping would take
 * the EPG load down with it". [EpgSnapshotCodec.decode] catches `EOFException` and `IOException`,
 * which covers a file that is too short. It does not cover a file whose *counts* are wrong, and
 * those are read straight out of the file as unbounded ints and handed to `ArrayList(capacity)`.
 *
 * The file is written by this app, so honest counts are bounded by what the parser will ever
 * produce: [XmlTvParser.MAX_CHANNELS] channels and [XmlTvParser.MAX_PROGRAMMES] programmes. A count
 * past those did not come from here.
 */
class EpgSnapshotCodecCorruptionTest {

    /** A v2 stream that is correct up to [channelCount], which is whatever a caller wants to claim. */
    private fun snapshotClaiming(channelCount: Int): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).apply {
            writeInt(2)
            writeUTF("fingerprint")
            writeLong(1_700_000_000_000L)
            writeBoolean(false)
            writeBoolean(false)
            writeInt(channelCount)
            flush()
        }
    }.toByteArray()

    private fun decode(bytes: ByteArray) = EpgSnapshotCodec.decode(ByteArrayInputStream(bytes))

    /**
     * One flipped byte in the count field is enough. `ArrayList(Int.MAX_VALUE)` allocates its
     * backing array up front, so this is an OutOfMemoryError on a phone with a 256MB heap - the one
     * the field reports come from - thrown out of a restore that runs at startup.
     */
    @Test
    fun `a channel count larger than the parser can produce is refused`() {
        assertNull(decode(snapshotClaiming(Int.MAX_VALUE)))
        assertNull(decode(snapshotClaiming(XmlTvParser.MAX_CHANNELS + 1)))
    }

    /** `ArrayList(-1)` throws IllegalArgumentException, which is neither of the two the decoder
     * guards against. */
    @Test
    fun `a negative channel count is refused`() {
        assertNull(decode(snapshotClaiming(-1)))
    }

    /** The counts nested inside a channel are read the same way and need the same treatment - here
     * the display-name count, which is a plain int in the middle of the record. */
    @Test
    fun `a nonsense display-name count is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(2)
                writeUTF("fingerprint")
                writeLong(1_700_000_000_000L)
                writeBoolean(false)
                writeBoolean(false)
                writeInt(1)
                writeUTF("ch1")
                writeInt(Int.MAX_VALUE)
                flush()
            }
        }.toByteArray()

        assertNull(decode(bytes))
    }

    /** And the programme counts, which are the largest numbers in the file and so the ones a
     * damaged byte hurts most. */
    @Test
    fun `a nonsense programme count is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(2)
                writeUTF("fingerprint")
                writeLong(1_700_000_000_000L)
                writeBoolean(false)
                writeBoolean(false)
                writeInt(0)
                writeInt(1)
                writeUTF("ch1")
                writeInt(XmlTvParser.MAX_PROGRAMMES + 1)
                flush()
            }
        }.toByteArray()

        assertNull(decode(bytes))
    }

    /** A group count is bounded by the channel count for the same reason: one group per channel. */
    @Test
    fun `a nonsense group count is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(2)
                writeUTF("fingerprint")
                writeLong(1_700_000_000_000L)
                writeBoolean(false)
                writeBoolean(false)
                writeInt(0)
                writeInt(Int.MAX_VALUE)
                flush()
            }
        }.toByteArray()

        assertNull(decode(bytes))
    }
}
