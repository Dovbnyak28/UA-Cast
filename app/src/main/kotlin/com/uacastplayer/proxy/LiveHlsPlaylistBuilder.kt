package com.uacastplayer.proxy

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private const val MILLIS_PER_SECOND = 1000.0

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
            val durationSeconds = segment.durationMillis / MILLIS_PER_SECOND
            builder.append(String.format(Locale.ROOT, "#EXTINF:%.3f,", durationSeconds)).append('\n')
            builder.append(segmentUrl(segment.sequence)).append('\n')
        }
        return builder.toString()
    }
}
