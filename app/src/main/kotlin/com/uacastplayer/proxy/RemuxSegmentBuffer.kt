package com.uacastplayer.proxy

private const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024

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
        while (totalBytes > maxBytes && segments.size > 1) {
            val evicted = segments.removeFirst()
            totalBytes -= evicted.bytes.size
        }
    }

    fun snapshot(): List<TsSegment> = segments.toList()

    fun segment(sequence: Int): TsSegment? = segments.firstOrNull { it.sequence == sequence }

    val firstSequence: Int? get() = segments.firstOrNull()?.sequence

    val isEmpty: Boolean get() = segments.isEmpty()
}
