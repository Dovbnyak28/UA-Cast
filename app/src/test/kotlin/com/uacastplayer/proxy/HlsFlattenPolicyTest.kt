package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replaying an HLS channel as one continuous stream, which is the only shape a DLNA renderer
 * accepts for a channel whose origin is genuinely HLS.
 *
 * Everything here is about not losing or repeating a second of television. A live playlist's window
 * slides, so "which segments are new" cannot be answered by comparing URIs - they repeat - and the
 * spec's own answer, `#EXT-X-MEDIA-SEQUENCE`, is what these tests pin.
 */
class HlsFlattenPolicyTest {

    /** A plain live media playlist; every test varies it with `copy()`, which keeps this helper
     * from growing a parameter per field. */
    private fun playlist(segments: List<String>, mediaSequence: Long = 0, target: Int? = 6) =
        HlsMediaPlaylist(
            segmentUris = segments,
            mediaSequence = mediaSequence,
            targetDurationSeconds = target,
            hasEndList = false,
            isMaster = false,
            hasEncryptedSegments = false,
            hasInitSegment = false,
        )

    // ---- what may be flattened at all ----

    @Test
    fun `an ordinary media playlist can be replayed`() {
        assertEquals(HlsFlattenPolicy.Verdict.Ok, HlsFlattenPolicy.verdictFor(playlist(listOf("1.ts", "2.ts"))))
    }

    /** A master lists variants, not media - one has to be chosen before any of this applies. */
    @Test
    fun `a master playlist asks for a variant rather than being refused`() {
        assertEquals(
            HlsFlattenPolicy.Verdict.NeedsVariant,
            HlsFlattenPolicy.verdictFor(playlist(listOf("720p.m3u8")).copy(isMaster = true)),
        )
    }

    /**
     * The two shapes where concatenation would produce a stream that is *wrong* rather than one
     * that fails - which is the worst outcome available, because the renderer would show noise
     * instead of an error.
     */
    @Test
    fun `encrypted segments and fragmented mp4 are refused, not attempted`() {
        val encrypted = HlsFlattenPolicy.verdictFor(playlist(listOf("1.ts")).copy(hasEncryptedSegments = true))
        val fmp4 = HlsFlattenPolicy.verdictFor(playlist(listOf("1.m4s")).copy(hasInitSegment = true))

        assertTrue(encrypted is HlsFlattenPolicy.Verdict.Unsupported)
        assertTrue(fmp4 is HlsFlattenPolicy.Verdict.Unsupported)
    }

    @Test
    fun `byte-range segments are refused because whole-object fetches would corrupt the stream`() {
        val verdict = HlsFlattenPolicy.verdictFor(
            playlist(listOf("shared.ts")).copy(hasByteRanges = true),
        )

        assertTrue(verdict is HlsFlattenPolicy.Verdict.Unsupported)
    }

    @Test
    fun `a playlist with no segments is refused`() {
        assertTrue(HlsFlattenPolicy.verdictFor(playlist(emptyList())) is HlsFlattenPolicy.Verdict.Unsupported)
    }

    @Test
    fun `a 200 error body without EXTM3U is not accepted as a media playlist`() {
        val parsed = HlsMediaPlaylistParser.parse("Access denied")

        assertFalse(parsed.hasPlaylistHeader)
        assertTrue(HlsFlattenPolicy.verdictFor(parsed) is HlsFlattenPolicy.Verdict.Unsupported)
    }

    @Test
    fun `a UTF-8 BOM before EXTM3U is normalized`() {
        val parsed = HlsMediaPlaylistParser.parse(
            "\uFEFF#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\na.ts\n",
        )

        assertTrue(parsed.hasPlaylistHeader)
        assertEquals(listOf("a.ts"), parsed.segmentUris)
        assertEquals(HlsFlattenPolicy.Verdict.Ok, HlsFlattenPolicy.verdictFor(parsed))
    }

    // ---- which segments come next ----

    @Test
    fun `a first read serves everything listed`() {
        val list = playlist(listOf("1.ts", "2.ts", "3.ts"), mediaSequence = 100)

        assertEquals(listOf("1.ts", "2.ts", "3.ts"), HlsFlattenPolicy.segmentsToServe(list, nextSequence = 0))
        assertEquals(103, HlsFlattenPolicy.sequenceAfterServing(list, 0))
    }

    /** The ordinary live case: polled again before the window moved, so there is nothing new. */
    @Test
    fun `a refresh with nothing new serves nothing`() {
        val list = playlist(listOf("1.ts", "2.ts", "3.ts"), mediaSequence = 100)

        assertEquals(emptyList<String>(), HlsFlattenPolicy.segmentsToServe(list, nextSequence = 103))
    }

    /** The window advanced by one; exactly one segment is owed. */
    @Test
    fun `a window that advanced serves only what is new`() {
        val list = playlist(listOf("2.ts", "3.ts", "4.ts"), mediaSequence = 101)

        assertEquals(listOf("4.ts"), HlsFlattenPolicy.segmentsToServe(list, nextSequence = 103))
        assertEquals(104, HlsFlattenPolicy.sequenceAfterServing(list, 103))
    }

    /**
     * Identical URIs across a refresh must not be mistaken for identical segments. A provider that
     * numbers its files modulo something republishes `1.ts` as a genuinely new segment, and matching
     * on the name would skip it.
     */
    @Test
    fun `repeating uris are told apart by their sequence numbers`() {
        val first = playlist(listOf("1.ts", "2.ts"), mediaSequence = 10)
        val afterFirst = HlsFlattenPolicy.sequenceAfterServing(first, 0)

        val second = playlist(listOf("2.ts", "1.ts"), mediaSequence = 11)

        assertEquals(
            "the second 1.ts is a new segment, not the one already served",
            listOf("1.ts"),
            HlsFlattenPolicy.segmentsToServe(second, afterFirst),
        )
    }

    /**
     * A reader too slow for the window - the origin dropped segments before they could be written.
     * That is a slow phone, not an error, and the stream resumes at the oldest thing still
     * published, exactly as any HLS client does.
     */
    @Test
    fun `a reader that fell behind the window resumes at its start`() {
        val list = playlist(listOf("50.ts", "51.ts"), mediaSequence = 500)

        assertEquals(listOf("50.ts", "51.ts"), HlsFlattenPolicy.segmentsToServe(list, nextSequence = 400))
        assertEquals(502, HlsFlattenPolicy.sequenceAfterServing(list, 400))
    }

    /** The sequence must never go backwards, or a later refresh would replay what was served. */
    @Test
    fun `serving a stale playlist does not rewind the reader`() {
        val stale = playlist(listOf("1.ts"), mediaSequence = 100)

        assertEquals(200, HlsFlattenPolicy.sequenceAfterServing(stale, nextSequence = 200))
    }

    @Test
    fun `one stale CDN window does not rewind a live replay`() {
        val cursor = HlsReplayCursor(nextSequence = 103)
        val stale = playlist(listOf("90.ts", "91.ts"), mediaSequence = 90)

        val selection = cursor.select(stale)

        assertEquals(emptyList<String>(), selection.segmentUris)
        assertFalse(selection.resetDetected)
        assertEquals(103, selection.cursor.nextSequence)
        assertEquals(1, selection.cursor.rollbackObservations)
    }

    @Test
    fun `three consecutive rollback windows recover after an encoder sequence reset`() {
        val resetWindows = listOf(
            playlist(listOf("0.ts", "1.ts"), mediaSequence = 0),
            playlist(listOf("1.ts", "2.ts"), mediaSequence = 1),
            playlist(listOf("2.ts", "3.ts"), mediaSequence = 2),
        )
        var cursor = HlsReplayCursor(nextSequence = 103)

        val selections = resetWindows.map { window ->
            cursor.select(window).also { cursor = it.cursor.afterServing(window) }
        }

        assertEquals(emptyList<String>(), selections[0].segmentUris)
        assertEquals(emptyList<String>(), selections[1].segmentUris)
        assertEquals(listOf("2.ts", "3.ts"), selections[2].segmentUris)
        assertTrue(selections[2].resetDetected)
        assertEquals(4, cursor.nextSequence)
        assertEquals(0, cursor.rollbackObservations)
    }

    @Test
    fun `a current window between stale responses clears rollback evidence`() {
        val initial = HlsReplayCursor(nextSequence = 103)
        val stale = playlist(listOf("90.ts"), mediaSequence = 90)
        val current = playlist(listOf("103.ts"), mediaSequence = 103)

        val afterStale = initial.select(stale).cursor.afterServing(stale)
        val recovered = afterStale.select(current)

        assertEquals(listOf("103.ts"), recovered.segmentUris)
        assertEquals(0, recovered.cursor.rollbackObservations)
        assertFalse(recovered.resetDetected)
    }

    @Test
    fun `a playlist with no media sequence tag starts at zero`() {
        val parsed = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\na.ts\n")

        assertEquals(0, parsed.mediaSequence)
        assertEquals(listOf("a.ts"), HlsFlattenPolicy.segmentsToServe(parsed, 0))
    }

    @Test
    fun `invalid negative media sequence is normalized to the spec default`() {
        val parsed = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:-5\n#EXTINF:4,\na.ts\n",
        )

        assertEquals(0, parsed.mediaSequence)
    }

    @Test
    fun `sequence end saturates instead of overflowing`() {
        val parsed = playlist(listOf("a.ts", "b.ts"), mediaSequence = Long.MAX_VALUE)

        assertEquals(Long.MAX_VALUE, parsed.nextSequenceAfter)
    }

    // ---- refresh pacing ----

    @Test
    fun `the refresh interval is half the target duration`() {
        assertEquals(3_000, HlsFlattenPolicy.refreshDelayMillis(playlist(listOf("1.ts"), target = 6)))
        assertEquals(5_000, HlsFlattenPolicy.refreshDelayMillis(playlist(listOf("1.ts"), target = 10)))
    }

    /** A feed declaring something absurd must not turn this into a request flood or a stall. */
    @Test
    fun `an absurd target duration is bounded at both ends`() {
        val flood = HlsFlattenPolicy.refreshDelayMillis(playlist(listOf("1.ts"), target = 1))
        val stall = HlsFlattenPolicy.refreshDelayMillis(playlist(listOf("1.ts"), target = 600))

        assertTrue("polling every ${flood}ms would be a flood", flood >= 500)
        assertTrue("waiting ${stall}ms would starve the stream", stall <= 10_000)
    }

    @Test
    fun `a playlist with no target duration still gets a sane interval`() {
        val delay = HlsFlattenPolicy.refreshDelayMillis(playlist(listOf("1.ts"), target = null))

        assertTrue(delay in 500..10_000)
    }
}

/**
 * Reading a media playlist far enough to replay it.
 *
 * Kept separate from the policy above because these are two different kinds of mistake: misreading
 * a tag, and mis-deciding what to do with it.
 */
class HlsMediaPlaylistParserTest {

    @Test
    fun `a live media playlist is read completely`() {
        val parsed = HlsMediaPlaylistParser.parse(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:8
            #EXT-X-MEDIA-SEQUENCE:2680
            #EXTINF:7.975,
            https://origin.example/seg2680.ts
            #EXTINF:7.941,
            seg2681.ts
            """.trimIndent(),
        )

        assertEquals(listOf("https://origin.example/seg2680.ts", "seg2681.ts"), parsed.segmentUris)
        assertEquals(2680, parsed.mediaSequence)
        assertEquals(8, parsed.targetDurationSeconds)
        assertEquals(2682, parsed.nextSequenceAfter)
        assertFalse(parsed.hasEndList)
        assertFalse(parsed.isMaster)
    }

    @Test
    fun `a finished playlist is recognised so replay can end rather than poll forever`() {
        val parsed = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXTINF:4,\na.ts\n#EXT-X-ENDLIST\n")

        assertTrue(parsed.hasEndList)
    }

    @Test
    fun `a master playlist is recognised by its variant tag`() {
        val parsed = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nhigh.m3u8\n",
        )

        assertTrue(parsed.isMaster)
        assertEquals(listOf("low.m3u8", "high.m3u8"), parsed.segmentUris)
    }

    @Test
    fun `encryption and an init segment are both spotted`() {
        val encrypted = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\"\n#EXTINF:4,\na.ts\n",
        )
        val fmp4 = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\na.m4s\n")

        assertTrue(encrypted.hasEncryptedSegments)
        assertTrue(fmp4.hasInitSegment)
    }

    @Test
    fun `a byte-range playlist is spotted`() {
        val parsed = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-BYTERANGE:75232@0\n#EXTINF:4,\nshared.ts\n",
        )

        assertTrue(parsed.hasByteRanges)
    }

    /** `METHOD=NONE` is the spec's way of switching encryption off partway through, and is not an
     * obstacle - reading it as one would refuse a stream that plays perfectly well. */
    @Test
    fun `a key tag that turns encryption off is not an obstacle`() {
        val parsed = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:4,\na.ts\n")

        assertFalse(parsed.hasEncryptedSegments)
        assertEquals(HlsFlattenPolicy.Verdict.Ok, HlsFlattenPolicy.verdictFor(parsed))
    }

    @Test
    fun `only an exact NONE key method disables encryption`() {
        val malformed = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-KEY:METHOD=NONE-SUCH\n#EXTINF:4,\na.ts\n",
        )
        val similarlyNamedTag = HlsMediaPlaylistParser.parse(
            "#EXTM3U\n#EXT-X-KEYFORMAT:METHOD=AES-128\n#EXTINF:4,\na.ts\n",
        )

        assertTrue(malformed.hasEncryptedSegments)
        assertFalse(similarlyNamedTag.hasEncryptedSegments)
    }

    /** A decimal target duration is against the spec but does occur - taking the whole part beats
     * discarding the tag and falling back to a guess. */
    @Test
    fun `a decimal target duration is read rather than discarded`() {
        val parsed = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10.0\n#EXTINF:10,\na.ts\n")

        assertEquals(10, parsed.targetDurationSeconds)
    }

    /** An update check runs this against whatever a proxy or portal returns; it must not throw. */
    @Test
    fun `rubbish is read as an empty playlist rather than throwing`() {
        for (text in listOf("", "not a playlist", "#EXTM3U", "#EXT-X-MEDIA-SEQUENCE:oops")) {
            val parsed = HlsMediaPlaylistParser.parse(text)
            assertTrue(parsed.segmentUris.isEmpty() || parsed.segmentUris == listOf("not a playlist"))
            assertEquals(0, parsed.mediaSequence)
        }
    }
}
