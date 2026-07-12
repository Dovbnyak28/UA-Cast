package com.uacastplayer.player

/** Seeking only makes sense for non-live, seekable content, and never while casting. */
object SeekPolicy {
    fun canSeek(isLive: Boolean, isSeekable: Boolean, isCasting: Boolean): Boolean =
        !isCasting && !isLive && isSeekable
}
