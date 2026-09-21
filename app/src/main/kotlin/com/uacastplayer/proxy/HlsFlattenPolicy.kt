package com.uacastplayer.proxy

/**
 * Turning an HLS channel into one continuous MPEG-TS response, decided as pure arithmetic.
 *
 * ## Why this exists
 *
 * A DLNA renderer plays a stream, not a manifest. Most sets that are not Samsung cannot read HLS at
 * all - this app's `AvTransportSoapBuilder` says so in as many words - and a field report put a
 * photograph to it: a Hisense VIDAA fetched the manifest the proxy served, refused
 * SetAVTransportURI, and displayed "Archivo no compatible" having never asked for a segment.
 *
 * Turning off the raw-TS remux fixed the channels whose *origin* is a continuous stream. It could
 * not fix the ones whose origin is genuinely HLS, because there the manifest is what the origin
 * hands out. For those, the only thing that helps is to do the client's job on the phone: read the
 * playlist, fetch its segments in order, and write their bytes into one endless response.
 *
 * ## What makes that sound
 *
 * MPEG-TS is a packet stream with no file header, so segments of one HLS variant concatenate into a
 * valid stream by construction - which is exactly what makes the reverse operation (this app's
 * [TsSegmenter]) possible. Four cases break that, and each is refused rather than served badly:
 *
 * - **encrypted segments** would need a key this app does not fetch, so the bytes are noise;
 * - **fragmented MP4** (`#EXT-X-MAP`) is not a packet stream and does not concatenate at all;
 * - **byte-range segments** need a distinct Range request per entry, not whole-object downloads;
 * - **a master playlist** lists variants, not media, so one has to be chosen first.
 *
 * Refusing means falling back to serving the manifest - no worse than today, and still correct for
 * a receiver that can read one.
 */
object HlsFlattenPolicy {

    /** Why a playlist cannot be replayed as a continuous stream, or [Ok]. */
    sealed interface Verdict {
        data object Ok : Verdict

        /** Its references are other playlists; pick a variant and re-evaluate. */
        data object NeedsVariant : Verdict

        /** Concatenating these bytes would produce a stream that is wrong rather than one that
         * fails, so it must not be attempted. */
        data class Unsupported(val reason: String) : Verdict
    }

    fun verdictFor(playlist: HlsMediaPlaylist): Verdict = when {
        !playlist.hasPlaylistHeader -> Verdict.Unsupported("missing #EXTM3U playlist header")
        playlist.isMaster -> Verdict.NeedsVariant
        playlist.hasEncryptedSegments -> Verdict.Unsupported("segments are encrypted")
        playlist.hasInitSegment -> Verdict.Unsupported("fragmented MP4, not MPEG-TS")
        playlist.hasByteRanges -> Verdict.Unsupported("byte-range segments require Range requests")
        playlist.segmentUris.isEmpty() -> Verdict.Unsupported("no segments listed")
        else -> Verdict.Ok
    }

    /**
     * Which of [playlist]'s segments have not been written yet, given the sequence number the
     * reader is due to serve next.
     *
     * Sequence numbers, never URIs, because a live playlist's window slides and its entries repeat:
     * the same `1234.ts` can reappear as a different segment on a provider that numbers modulo
     * something, and matching on the URI would either replay it or skip it. `#EXT-X-MEDIA-SEQUENCE`
     * is the spec's own answer to this question.
     *
     * @param nextSequence what to serve next; below the window means segments expired before they
     *   could be written, which is a slow reader rather than an error - the stream resumes at the
     *   oldest thing still listed, exactly as any HLS client does.
     */
    fun segmentsToServe(playlist: HlsMediaPlaylist, nextSequence: Long): List<String> {
        if (playlist.segmentUris.isEmpty()) return emptyList()
        val firstAvailable = playlist.mediaSequence
        val lastAvailable = playlist.nextSequenceAfter - 1
        return when {
            // Everything listed has already been served - the ordinary case for a live playlist
            // polled faster than it advances.
            nextSequence > lastAvailable -> emptyList()
            // The window moved past what was owed. Resume from its start rather than trying to
            // fetch segments the origin no longer publishes.
            nextSequence < firstAvailable -> playlist.segmentUris
            else -> playlist.segmentUris.drop((nextSequence - firstAvailable).toInt())
        }
    }

    /** Where a reader resumes from after serving everything [segmentsToServe] returned. */
    fun sequenceAfterServing(playlist: HlsMediaPlaylist, nextSequence: Long): Long =
        maxOf(nextSequence, playlist.nextSequenceAfter)

    /**
     * How long to wait before asking for the playlist again.
     *
     * Half the target duration, which is what a live HLS client does: the spec allows a playlist to
     * gain one segment per target duration, and polling at that exact rate drifts into arriving
     * just too late and starving the stream. Bounded at both ends so a feed declaring one second -
     * or six hundred - cannot turn this into a request flood or a stall.
     */
    fun refreshDelayMillis(playlist: HlsMediaPlaylist): Long {
        val target = playlist.targetDurationSeconds ?: DEFAULT_TARGET_DURATION_SECONDS
        val half = target * MILLIS_PER_SECOND / 2
        return half.coerceIn(MIN_REFRESH_MILLIS, MAX_REFRESH_MILLIS)
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val DEFAULT_TARGET_DURATION_SECONDS = 6
    private const val MIN_REFRESH_MILLIS = 500L
    private const val MAX_REFRESH_MILLIS = 10_000L
}
