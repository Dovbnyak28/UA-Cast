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
        appendStartOffset(builder, segments)
        for (segment in segments) {
            // Sequence numbering continues across the gap per the live-HLS spec - only the tag
            // changes, #EXT-X-MEDIA-SEQUENCE above is untouched.
            if (segment.discontinuity) builder.append("#EXT-X-DISCONTINUITY\n")
            builder.append("#EXTINF:").appendExtinfDuration(segment.durationMillis).append(",\n")
            builder.append(segmentUrl(segment.sequence)).append('\n')
        }
        return builder.toString()
    }

    /**
     * Tells the receiver where in the window to begin, instead of leaving it to the default of
     * three segments back from the live edge.
     *
     * That default is the whole problem on a constrained upstream. Three segments is however many
     * seconds three segments happen to be - ~15s at the 5s target, but only ~10s on a high-bitrate
     * channel where [TsSegmenter]'s 4MB cap forces shorter cuts - and it is all the slack the
     * receiver ever has. The proxy fetches through whatever the phone's connection is, a VPN very
     * much included, so any stretch where upstream delivers slower than real time drains that slack
     * and the receiver stalls. Starting further back does not delay anything the user notices on a
     * live channel; it just hands the receiver more to chew on.
     *
     * Half the window, rather than a fixed number of seconds, because the window itself varies with
     * bitrate (see [RemuxSegmentBuffer]) - a fixed offset would either sit outside a short window or
     * waste most of a long one. Half is always safely inside, and with the six-segment floor it is
     * never less than the three-segment default it replaces.
     */
    private fun appendStartOffset(builder: StringBuilder, segments: List<TsSegment>) {
        if (segments.size < MIN_SEGMENTS_FOR_START_OFFSET) return
        val windowMillis = segments.sumOf { it.durationMillis }
        val offsetMillis = windowMillis / 2
        if (offsetMillis <= 0) return
        builder.append("#EXT-X-START:TIME-OFFSET=-")
            .appendExtinfDuration(offsetMillis)
            .append(",PRECISE=YES\n")
    }
}

/** Below this the window is too short for a start offset to buy anything over the receiver's own
 * default, and pointing at its first segment would sit right on the eviction edge. */
private const val MIN_SEGMENTS_FOR_START_OFFSET = 4

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
