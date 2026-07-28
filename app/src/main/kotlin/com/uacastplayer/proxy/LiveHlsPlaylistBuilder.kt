package com.uacastplayer.proxy

import kotlin.math.ceil
import kotlin.math.max

private const val MILLIS_PER_SECOND = 1000.0
private const val MILLIS_PER_SECOND_LONG = 1000L
private const val MILLIS_FRACTION_DIGITS = 3

/**
 * Builds a live (no `#EXT-X-ENDLIST`) HLS media playlist for a [RemuxSegmentBuffer]'s current
 * contents. `#EXT-X-MEDIA-SEQUENCE` is the first listed segment's own sequence number - per the
 * HLS spec this is how a client detects segments have rolled off the front of a live window.
 * `#EXT-X-TARGETDURATION` must be an integer number of seconds no smaller than any listed
 * segment's actual duration, so it's derived from the segments actually present rather than a
 * fixed constant - a keyframe-forced early cut (see [TsSegmenter]) can occasionally produce a
 * segment longer than the configured target.
 */
object LiveHlsPlaylistBuilder {

    fun build(segments: List<TsSegment>, segmentUrl: (Int) -> String, configuredTargetDurationSeconds: Int): String {
        val longestSegmentSeconds = segments.maxOfOrNull { it.durationMillis / MILLIS_PER_SECOND } ?: 0.0
        val targetDuration = max(configuredTargetDurationSeconds, ceil(longestSegmentSeconds).toInt())
        val mediaSequence = segments.firstOrNull()?.sequence ?: 0

        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        builder.append("#EXT-X-VERSION:3\n")
        builder.append("#EXT-X-TARGETDURATION:$targetDuration\n")
        builder.append("#EXT-X-MEDIA-SEQUENCE:$mediaSequence\n")
        for (segment in segments) {
            // Sequence numbering continues across the gap per the live-HLS spec - only the tag
            // changes, #EXT-X-MEDIA-SEQUENCE above is untouched.
            if (segment.discontinuity) builder.append("#EXT-X-DISCONTINUITY\n")
            builder.append("#EXTINF:").appendExtinfDuration(segment.durationMillis).append(",\n")
            builder.append(segmentUrl(segment.sequence)).append('\n')
        }
        return builder.toString()
    }
}

/** Appends `durationMillis` as `"<seconds>.<millis, zero-padded to 3 digits>"` (e.g. "5.023") -
 * `#EXTINF` needs exactly this shape, and this avoids `String.format`'s Locale-aware Formatter
 * machinery (format-string parsing, a Locale-specific decimal formatter) on every segment of
 * every playlist rebuild, which polling a live remux session does every few seconds. */
private fun StringBuilder.appendExtinfDuration(durationMillis: Long): StringBuilder {
    val wholeSeconds = durationMillis / MILLIS_PER_SECOND_LONG
    val fractionMillis = (durationMillis % MILLIS_PER_SECOND_LONG).toString()
    append(wholeSeconds).append('.')
    repeat(MILLIS_FRACTION_DIGITS - fractionMillis.length) { append('0') }
    return append(fractionMillis)
}
