package com.uacastplayer.proxy

/**
 * The parts of an HLS media playlist needed to replay it as one continuous stream.
 *
 * @param mediaSequence `#EXT-X-MEDIA-SEQUENCE`, the sequence number of [segmentUris]'s first entry.
 *   Absent means 0, which the spec says explicitly. This is the only reliable way to tell which
 *   segments a refreshed live playlist has *already served* - the URIs themselves repeat across
 *   channels and can even repeat within one stream.
 * @param targetDurationSeconds `#EXT-X-TARGETDURATION`, which the spec requires in every media
 *   playlist. Absent leaves it null and the caller falls back to a default refresh interval.
 * @param hasEndList a `#EXT-X-ENDLIST` marks a finished (VOD) playlist - one that will never grow,
 *   so replaying it ends rather than polling forever.
 * @param isMaster carries `#EXT-X-STREAM-INF`, so its references are playlists rather than media
 *   and one of them has to be chosen before any of this applies.
 * @param hasEncryptedSegments carries an `#EXT-X-KEY` naming a method other than NONE.
 * @param hasInitSegment carries `#EXT-X-MAP`, i.e. fragmented MP4 rather than MPEG-TS.
 */
data class HlsMediaPlaylist(
    val segmentUris: List<String>,
    val mediaSequence: Long,
    val targetDurationSeconds: Int?,
    val hasEndList: Boolean,
    val isMaster: Boolean,
    val hasEncryptedSegments: Boolean,
    val hasInitSegment: Boolean,
) {
    /** The sequence number one past the last listed segment - where a reader that consumed this
     * whole playlist should resume from on the next refresh. */
    val nextSequenceAfter: Long get() = mediaSequence + segmentUris.size
}

/**
 * Reads a media playlist far enough to replay it, and no further.
 *
 * Deliberately not a general HLS parser. Everything here exists to answer one question - which
 * bytes come next, in order - so durations, bandwidth, subtitle renditions and the rest are skipped
 * rather than modelled. The three "cannot do this" flags are the exception: each is a case where
 * concatenating segments would produce a stream that is silently wrong rather than one that fails,
 * so they must be detected and refused, never ignored.
 */
object HlsMediaPlaylistParser {

    private const val TAG_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:"
    private const val TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION:"
    private const val TAG_ENDLIST = "#EXT-X-ENDLIST"
    private const val TAG_STREAM_INF = "#EXT-X-STREAM-INF"
    private const val TAG_KEY = "#EXT-X-KEY"
    private const val TAG_MAP = "#EXT-X-MAP"

    fun parse(text: String): HlsMediaPlaylist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return HlsMediaPlaylist(
            segmentUris = lines.filterNot { it.startsWith("#") },
            mediaSequence = lines.firstNotNullOfOrNull { line ->
                line.takeIf { it.startsWith(TAG_MEDIA_SEQUENCE) }
                    ?.removePrefix(TAG_MEDIA_SEQUENCE)?.trim()?.toLongOrNull()
            } ?: 0L,
            targetDurationSeconds = lines.firstNotNullOfOrNull { line ->
                line.takeIf { it.startsWith(TAG_TARGET_DURATION) }
                    ?.removePrefix(TAG_TARGET_DURATION)?.trim()
                    // A decimal target duration is against the spec but does occur; taking the whole
                    // part is better than discarding the tag and falling back to a guess.
                    ?.substringBefore('.')?.toIntOrNull()?.takeIf { it > 0 }
            },
            hasEndList = lines.any { it.startsWith(TAG_ENDLIST) },
            isMaster = lines.any { it.startsWith(TAG_STREAM_INF) },
            // METHOD=NONE is the spec's way of saying "encryption stops here", and appears in
            // streams that switch encryption off partway. It is not an obstacle.
            hasEncryptedSegments = lines.any {
                it.startsWith(TAG_KEY) && !it.substringAfter("METHOD=").startsWith("NONE")
            },
            hasInitSegment = lines.any { it.startsWith(TAG_MAP) },
        )
    }
}
