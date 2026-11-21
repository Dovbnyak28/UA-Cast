package com.uacastplayer.proxy

import com.uacastplayer.core.cast.TsProgramInfoParser
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzzes the byte parsers the proxy points at a third-party origin, for the one property they all
 * have to hold: **they do not throw.**
 *
 * That property is load-bearing here in a way it is not for the playlist parser, because failing it
 * is invisible. `RawTsRemuxSession.readUntilDisconnected` catches `Exception` - not `IOException` -
 * around every call into [TsSegmenter], on purpose, so that a corrupt packet ends the read cycle
 * rather than the process. The cost of that safety net is that a genuine index bug in here does not
 * crash and does not report: it ends the connection, the session reconnects, and the user gets a
 * channel that rebuffers every few seconds with one log line that blames the network. Nothing else
 * would ever point at the parser.
 *
 * So this is a net under a net, and it is deliberately about survival rather than about answers.
 * What a parser *returns* for a stream of noise is a judgement call; that it returns at all is not.
 * The handful of value assertions below are only the ones where a wrong answer would be a silent
 * fault rather than a matter of taste.
 */
class ProxyParserFuzzTest {

    private companion object {
        const val PACKET_SIZE = 188
        const val SYNC_BYTE = 0x47.toByte()
        const val CASES = 400
    }

    private fun packets(random: Random, count: Int, sync: Boolean): ByteArray {
        val data = ByteArray(count * PACKET_SIZE) { random.nextInt(0, 256).toByte() }
        if (sync) {
            for (i in 0 until count) data[i * PACKET_SIZE] = SYNC_BYTE
        }
        return data
    }

    // ---------- TsSegmenter ----------

    /**
     * Well-formed only in that every packet starts with the sync byte - everything the segmenter
     * reads after it (PID, adaptation field length, section lengths, PCR) is noise, which is the
     * shape that reaches the bounds arithmetic hardest.
     */
    @Test
    fun segmenterSurvivesSyncAlignedNoise() {
        val random = Random(20260817)
        repeat(CASES) {
            val segmenter = TsSegmenter()
            val data = packets(random, random.nextInt(1, 40), sync = true)
            var offset = 0
            while (offset + PACKET_SIZE <= data.size) {
                segmenter.feed(data, offset)
                offset += PACKET_SIZE
            }
            segmenter.flush()
            segmenter.onReconnect()
        }
    }

    /** Unaligned bytes, fed at every offset rather than every 188th - the segmenter is documented
     * to ignore anything that is not a sync-aligned packet, and ignoring is not the same as
     * surviving until it has been tried. */
    @Test
    fun segmenterSurvivesBeingFedAtEveryOffset() {
        val random = Random(20260818)
        repeat(CASES) {
            val segmenter = TsSegmenter()
            val data = packets(random, 3, sync = false)
            for (offset in data.indices) {
                segmenter.feed(data, offset)
            }
        }
    }

    /**
     * Offsets past the end, and negative ones - `feed` documents that anything which is not a
     * 188-byte packet at [offset] is ignored.
     *
     * The large offset is the one that found something. The guard read
     * `offset + PACKET_SIZE > data.size`, and that addition overflows: at `Int.MAX_VALUE - 1` the
     * sum is negative, so the check passed and the very next line threw
     * `Index 2147483646 out of bounds for length 188`.
     *
     * **No production caller can reach it today** - `RawTsRemuxSession.consumePackets` walks a
     * buffer of about 64KB and never offers an offset near that. It is written down here because a
     * bounds guard that fails on its own stated contract is worth one line to close, and because
     * this is the shape that failure always takes: the overflow is in the check, not in the read.
     */
    @Test
    fun segmenterRefusesOffsetsOutsideTheBuffer() {
        val segmenter = TsSegmenter()
        val data = ByteArray(PACKET_SIZE) { if (it == 0) SYNC_BYTE else 0 }

        for (offset in listOf(-1, 1, PACKET_SIZE, PACKET_SIZE * 2, Int.MAX_VALUE - 1, Int.MAX_VALUE)) {
            assertEquals("offset $offset should have been ignored", null, segmenter.feed(data, offset))
        }
    }

    /** The control: the guard rejects everything above without also rejecting the one offset that
     * is genuinely a packet, which is the way a bounds fix usually goes wrong. */
    @Test
    fun segmenterStillAcceptsAPacketAtAValidOffset() {
        val segmenter = TsSegmenter(targetDurationMillis = 1, maxSegmentBytes = PACKET_SIZE)
        val data = ByteArray(PACKET_SIZE * 2)
        data[0] = SYNC_BYTE
        data[PACKET_SIZE] = SYNC_BYTE

        segmenter.feed(data, 0)
        segmenter.feed(data, PACKET_SIZE)

        assertNotNull("both packets were ignored, so nothing was buffered", segmenter.flush())
    }

    /** A packet whose adaptation field claims to be longer than the packet - the field is a byte, so
     * it can claim up to 255 of a 188-byte packet, and every section read starts by skipping it. */
    @Test
    fun segmenterSurvivesAnAdaptationFieldLongerThanThePacket() {
        val random = Random(20260819)
        repeat(CASES) {
            val segmenter = TsSegmenter()
            val packet = ByteArray(PACKET_SIZE) { random.nextInt(0, 256).toByte() }
            packet[0] = SYNC_BYTE
            packet[1] = 0x40 // payload_unit_start, PID 0 (PAT)
            packet[2] = 0x00
            packet[3] = 0x30 // adaptation field + payload
            packet[4] = 0xFF.toByte() // adaptation_field_length = 255, past the packet's own end
            segmenter.feed(packet, 0)
        }
    }

    /** A PAT/PMT pair whose section lengths and program_info_length are noise, which is what drives
     * the two cursor walks that read program tables. */
    @Test
    fun segmenterSurvivesProgramTablesWithNonsenseLengths() {
        val random = Random(20260820)
        repeat(CASES) {
            val segmenter = TsSegmenter()
            for (tableId in listOf(0x00, 0x02)) {
                val packet = ByteArray(PACKET_SIZE) { random.nextInt(0, 256).toByte() }
                packet[0] = SYNC_BYTE
                packet[1] = 0x40
                packet[2] = 0x00
                packet[3] = 0x10 // payload only
                packet[4] = 0x00 // pointer_field
                packet[5] = tableId.toByte()
                segmenter.feed(packet, 0)
            }
        }
    }

    // ---------- TsProgramInfoParser ----------

    @Test
    fun programInfoParserSurvivesNoise() {
        val random = Random(20260821)
        repeat(CASES) {
            TsProgramInfoParser.parse(packets(random, random.nextInt(0, 12), sync = random.nextBoolean()))
        }
        for (size in listOf(0, 1, 187, 188, 189, 375)) {
            TsProgramInfoParser.parse(ByteArray(size) { SYNC_BYTE })
        }
    }

    // ---------- sniffers ----------

    @Test
    fun sniffersSurviveNoiseAndAgreeWithThemselves() {
        val random = Random(20260822)
        repeat(CASES) {
            val bytes = ByteArray(random.nextInt(0, 2048)) { random.nextInt(0, 256).toByte() }
            MpegTsSniffer.looksLikeMpegTs(bytes)
            PlaylistDetector.isPlaylist(null, bytes)
        }
        for (size in listOf(0, 1, 3, 4, 187, 188)) {
            MpegTsSniffer.looksLikeMpegTs(ByteArray(size))
            PlaylistDetector.isPlaylist(null, ByteArray(size))
        }
    }

    // ---------- HLS text ----------

    @Test
    fun hlsParserSurvivesLineSoup() {
        val random = Random(20260823)
        val fragments = listOf(
            "#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:", "#EXT-X-TARGETDURATION:-1",
            "#EXT-X-TARGETDURATION:99999999999999999999", "#EXT-X-TARGETDURATION:10.5",
            "#EXT-X-MEDIA-SEQUENCE:", "#EXT-X-MEDIA-SEQUENCE:-5",
            "#EXT-X-MEDIA-SEQUENCE:99999999999999999999",
            "#EXT-X-KEY", "#EXT-X-KEY:METHOD=", "#EXT-X-KEY:METHOD=NONE", "#EXT-X-KEY:METHOD=AES-128",
            "#EXT-X-MAP", "#EXT-X-MAP:URI=", "#EXT-X-STREAM-INF", "#EXT-X-ENDLIST",
            "#EXTINF:", "#EXTINF:-1,", "seg.ts", "", " ", "\t", " ", "\uD83D",
            "a".repeat(5000), "URI=\"", "://", "%%", "..",
        )
        repeat(CASES) {
            val text = (0 until random.nextInt(0, 40)).joinToString("\n") { fragments.random(random) }
            val playlist = HlsMediaPlaylistParser.parse(text)
            HlsFlattenPolicy.verdictFor(playlist)
            HlsFlattenPolicy.segmentsToServe(playlist, random.nextLong(-10, 100))
            HlsFlattenPolicy.refreshDelayMillis(playlist)
            M3u8Rewriter.rewrite(text, "http://origin.test/live/a.m3u8") { "http://127.0.0.1/x" }
            PlaylistUnwrapPolicy.unwrapTarget(text, "http://origin.test/live/a.m3u8")
        }
    }

    /**
     * A refresh delay has to stay inside its own bounds whatever the feed declares, because it is a
     * sleep in a loop: a zero would spin against the origin, and an hour would stall the stream.
     */
    @Test
    fun refreshDelayStaysWithinItsBoundsForAnyDeclaredTarget() {
        for (declared in listOf("0", "-1", "1", "6", "600", "2147483647", "x", "", "10.5")) {
            val delay = HlsFlattenPolicy.refreshDelayMillis(
                HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:$declared\n#EXTINF:4,\na.ts\n"),
            )
            assertTrue("target '$declared' produced $delay ms", delay in 500..10_000)
        }
    }

    /**
     * The reader's position never goes backwards, whatever the playlist claims about its own
     * sequence numbers - a live window that appears to rewind would re-serve bytes the receiver has
     * already been given, which is a stutter rather than an error.
     */
    @Test
    fun theServingPositionNeverMovesBackwards() {
        val random = Random(20260824)
        repeat(CASES) {
            val first = random.nextLong(-100, 1_000_000)
            val count = random.nextInt(0, 8)
            val text = buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-MEDIA-SEQUENCE:$first")
                repeat(count) { appendLine("#EXTINF:4,"); appendLine("s$it.ts") }
            }
            val playlist = HlsMediaPlaylistParser.parse(text)
            val position = random.nextLong(-10, 1_000_010)
            val after = HlsFlattenPolicy.sequenceAfterServing(playlist, position)
            assertTrue("position went backwards: $position -> $after", after >= position)

            val toServe = HlsFlattenPolicy.segmentsToServe(playlist, position)
            assertNotNull(toServe)
            assertTrue("served more segments than the playlist lists", toServe.size <= playlist.segmentUris.size)
        }
    }
}
