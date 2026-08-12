package com.uacastplayer.playlist

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A damaged playlist file must be refused, not believed.
 *
 * Both of these decoders read their collection lengths as bare ints and handed them to
 * `ArrayList(capacity)`, which allocates up front - so a negative one threw
 * `IllegalArgumentException` and a large one `OutOfMemoryError`, past the `EOFException` and
 * `IOException` each decoder catches. This is the restore that runs on every launch, and its
 * failure mode was the app going down rather than the playlist being loaded again from its source.
 *
 * See [com.uacastplayer.core.io.readCountField], and its sibling test for the EPG snapshot, which
 * has real ceilings to check against where these two have none.
 */
class PlaylistCodecCorruptionTest {

    private fun bytes(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).apply(write).flush() }.toByteArray()

    private fun snapshotV2Claiming(channelCount: Int): ByteArray = bytes {
        writeInt(2)
        writeUTF("fingerprint")
        writeBoolean(true)
        writeUTF("http://example.test/list.m3u")
        writeLong(1_700_000_000_000L)
        writeInt(0)
        writeInt(channelCount)
    }

    private fun snapshotV1Claiming(channelCount: Int): ByteArray = bytes {
        writeInt(1)
        writeUTF("fingerprint")
        writeLong(1_700_000_000_000L)
        writeInt(0)
        writeInt(channelCount)
    }

    private fun sourcesClaiming(count: Int): ByteArray = bytes {
        writeInt(1)
        writeInt(count)
    }

    @Test
    fun `a snapshot claiming more channels than exist is refused, not allocated for`() {
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(snapshotV2Claiming(Int.MAX_VALUE))))
    }

    @Test
    fun `a snapshot claiming a negative channel count is refused`() {
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(snapshotV2Claiming(-1))))
    }

    /** The older format is still read on upgrade, so it needs the same treatment. */
    @Test
    fun `a v1 snapshot is guarded the same way`() {
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(snapshotV1Claiming(Int.MAX_VALUE))))
        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(snapshotV1Claiming(-1))))
    }

    /** A negative skipped-line count is not a crash on its own - nothing is allocated from it - but
     * it is a number this app never wrote, and a file carrying one is not one to trust the rest of. */
    @Test
    fun `a negative skipped-line count is refused`() {
        val corrupt = bytes {
            writeInt(2)
            writeUTF("fingerprint")
            writeBoolean(false)
            writeLong(1_700_000_000_000L)
            writeInt(-5)
            writeInt(0)
        }

        assertNull(PlaylistSnapshotCodec.decode(ByteArrayInputStream(corrupt)))
    }

    @Test
    fun `saved sources with a nonsense count come back empty instead of throwing`() {
        assertTrue(PlaylistSourceCodec.decode(ByteArrayInputStream(sourcesClaiming(Int.MAX_VALUE))).isEmpty())
        assertTrue(PlaylistSourceCodec.decode(ByteArrayInputStream(sourcesClaiming(-1))).isEmpty())
    }

    /**
     * The guard must not cost the honest case anything. A playlist far larger than any real one
     * still round-trips, which is the half that a ceiling invented for safety would have broken.
     */
    @Test
    fun `a very large but honest playlist still round-trips`() {
        val channels = (1..50_000).map {
            M3uChannel(displayName = "Channel $it", streamUrl = "http://example.test/$it")
        }
        val snapshot = PlaylistSnapshot(
            sourceFingerprint = "fingerprint",
            savedAtEpochMillis = 1_700_000_000_000L,
            channels = channels,
            skippedLineCount = 0,
            sourceUrl = null,
        )
        val encoded = ByteArrayOutputStream().also { PlaylistSnapshotCodec.encode(snapshot, it) }.toByteArray()

        val decoded = PlaylistSnapshotCodec.decode(ByteArrayInputStream(encoded))

        assertEquals(50_000, decoded?.channels?.size)
        assertEquals("Channel 50000", decoded?.channels?.last()?.displayName)
    }
}
