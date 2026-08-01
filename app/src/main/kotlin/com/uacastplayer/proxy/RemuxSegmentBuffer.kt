package com.uacastplayer.proxy

// Raised from 20MB. A byte ceiling alone decides the window in *bytes*, but what matters to the
// receiver is the window in *seconds*, and the two only agree at one bitrate. At 3 Mbit/s 20MB held
// ~10 segments (~50s, fine); at 10 Mbit/s it held 5 (~16s), and a client starting three back from
// the live edge then sat two segments from the eviction edge - any jitter and the segment it asked
// for was already gone, which is the 404 ProxyServer logs as "Remux segment miss".
//
// The extra memory is largely borrowed rather than new: local playback is stopped for the duration
// of a cast (see PlayerViewModel.handleCastSideEffect), so ExoPlayer is not holding its own 8-24MB
// of buffered media at the same time.
private const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024

// Floor on the window regardless of bitrate, so a high-bitrate channel cannot be squeezed down to
// an unusable playlist by the byte ceiling. Three is the bare minimum a live playlist can have (a
// client starts three back from the live edge, so fewer leaves it nothing to start on); six gives
// that client three more segments of margin before the oldest rolls off underneath it.
//
// Eviction stops here even when that exceeds [maxBytes] - TsSegmenter caps a segment at 4MB, so the
// worst case is bounded at 24MB and only reachable on a channel whose bitrate already made the byte
// ceiling the wrong tool.
private const val MIN_SEGMENTS = 6

/**
 * A sliding window of the most recent [TsSegment]s a [TsSegmenter] has produced, capped at
 * [maxBytes] total - old segments are evicted oldest-first, same idea as [ProxyServer]'s resource
 * map, so a channel that's been remuxing for hours doesn't grow the process's memory without
 * bound. The oldest surviving segment's [firstSequence] is exactly what
 * `#EXT-X-MEDIA-SEQUENCE` needs (see [LiveHlsPlaylistBuilder]) - a live playlist's media sequence
 * is defined as the sequence number of its first listed segment, which shifts up every time this
 * buffer evicts one.
 */
class RemuxSegmentBuffer(private val maxBytes: Long = DEFAULT_MAX_BYTES) {

    private val segments = ArrayDeque<TsSegment>()
    private var totalBytes = 0L

    fun add(segment: TsSegment) {
        segments.addLast(segment)
        totalBytes += segment.bytes.size
        while (totalBytes > maxBytes && segments.size > MIN_SEGMENTS) {
            val evicted = segments.removeFirst()
            totalBytes -= evicted.bytes.size
        }
    }

    fun snapshot(): List<TsSegment> = segments.toList()

    fun segment(sequence: Int): TsSegment? = segments.firstOrNull { it.sequence == sequence }

    val firstSequence: Int? get() = segments.firstOrNull()?.sequence

    val isEmpty: Boolean get() = segments.isEmpty()
}
