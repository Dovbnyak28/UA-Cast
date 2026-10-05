package com.uacastplayer.proxy

/** One budget for every path that buffers an HLS manifest, whether it rewrites or replays it. */
internal const val MAX_HLS_PLAYLIST_BYTES = 4 * 1024 * 1024
internal const val UTF8_BOM = "\uFEFF"

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
 * @param hasByteRanges carries `#EXT-X-BYTERANGE`, whose entries cannot be fetched correctly
 *   without forwarding an HTTP Range header for each segment.
 * @param hasPlaylistHeader whether the first non-blank line is the mandatory `#EXTM3U` signature.
 */
data class HlsMediaPlaylist(
    val segmentUris: List<String>,
    val mediaSequence: Long,
    val targetDurationSeconds: Int?,
    val hasEndList: Boolean,
    val isMaster: Boolean,
    val hasEncryptedSegments: Boolean,
    val hasInitSegment: Boolean,
    val hasByteRanges: Boolean = false,
    val hasPlaylistHeader: Boolean = true,
) {
    /** The sequence number one past the last listed segment - where a reader that consumed this
     * whole playlist should resume from on the next refresh. */
    val nextSequenceAfter: Long
        get() {
            val count = segmentUris.size.toLong()
            return if (mediaSequence > Long.MAX_VALUE - count) Long.MAX_VALUE else mediaSequence + count
        }
}

/**
 * Reads a media playlist far enough to replay it, and no further.
 *
 * Deliberately not a general HLS parser. Everything here exists to answer one question - which
 * bytes come next, in order - so durations, bandwidth, subtitle renditions and the rest are skipped
 * rather than modelled. The "cannot do this" flags are the exception: each is a case where
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
    private const val TAG_BYTERANGE = "#EXT-X-BYTERANGE"
    private const val PLAYLIST_HEADER = "#EXTM3U"
    private val keyMethodPattern = Regex("(?:^|,)\\s*METHOD\\s*=\\s*([^,\\s]+)", RegexOption.IGNORE_CASE)

    fun parse(text: String): HlsMediaPlaylist {
        HlsPlaylistBudget.requireAccepted(text)
        val parsed = ParsedLines()
        text.removePrefix(UTF8_BOM)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parsed::accept)
        return parsed.build()
    }

    /** Retain only URI lines, not two full manifest lists alongside the original text. */
    private class ParsedLines {
        private val uris = mutableListOf<String>()
        private var first: String? = null
        private var sequence: Long? = null
        private var duration: Int? = null
        private var ended = false
        private var master = false
        private var encrypted = false
        private var init = false
        private var ranges = false

        fun accept(line: String) {
            if (first == null) first = line
            when {
                !line.startsWith('#') -> uris.add(line)
                line.startsWith(TAG_MEDIA_SEQUENCE) -> if (sequence == null) {
                    sequence = line.removePrefix(TAG_MEDIA_SEQUENCE).trim().toLongOrNull()?.takeIf { it >= 0 }
                }
                line.startsWith(TAG_TARGET_DURATION) -> if (duration == null) {
                    // Tolerate providers with decimal durations while preserving first-valid semantics.
                    duration = line.removePrefix(TAG_TARGET_DURATION).trim().substringBefore('.')
                        .toIntOrNull()?.takeIf { it > 0 }
                }
                line.startsWith(TAG_ENDLIST) -> ended = true
                line.startsWith(TAG_STREAM_INF) -> master = true
                declaresEncryption(line) -> encrypted = true
                line.startsWith(TAG_MAP) -> init = true
                line.startsWith(TAG_BYTERANGE) -> ranges = true
            }
        }

        fun build() = HlsMediaPlaylist(
            segmentUris = uris,
            mediaSequence = sequence ?: 0,
            targetDurationSeconds = duration,
            hasEndList = ended,
            isMaster = master,
            hasEncryptedSegments = encrypted,
            hasInitSegment = init,
            hasByteRanges = ranges,
            hasPlaylistHeader = first == PLAYLIST_HEADER,
        )
    }

    private fun declaresEncryption(line: String): Boolean {
        if (!line.startsWith("$TAG_KEY:")) return false
        val method = keyMethodPattern.find(line.substringAfter(':'))?.groupValues?.getOrNull(1)
            ?.trim('"')
        return !method.equals("NONE", ignoreCase = true)
    }
}
