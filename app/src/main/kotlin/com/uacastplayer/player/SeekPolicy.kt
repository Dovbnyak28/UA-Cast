package com.uacastplayer.player

/** Seeking only makes sense for non-live, seekable content, and never while casting. */
object SeekPolicy {
    fun canSeek(isLive: Boolean, isSeekable: Boolean, isCasting: Boolean): Boolean =
        !isCasting && !isLive && isSeekable
    fun relativeTarget(positionMs: Long, durationMs: Long, offsetMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        val position = positionMs.coerceIn(0L, duration)
        return position + offsetMs.coerceIn(-position, duration - position)
    }

}
