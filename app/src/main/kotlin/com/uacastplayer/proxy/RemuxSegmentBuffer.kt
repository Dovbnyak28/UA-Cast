package com.uacastplayer.proxy

private const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024

// A live HLS playlist needs at least three segments to be usable at all: a client starts playback
// three back from the live edge, so a shorter window leaves it nothing to start on. [maxBytes] is a
// memory ceiling, and on a high-bitrate channel it can be reached with very few segments held - so
// eviction has to stop here even when that means briefly exceeding the ceiling, rather than
// shrinking the window to something the receiver cannot play. Segments are capped at 4MB each (see
// TsSegmenter), so the worst-case overshoot is bounded and small.
private const val MIN_SEGMENTS = 3

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
