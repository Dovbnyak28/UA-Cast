package com.uacastplayer.player

/** Keep speculative network work out of preparation, rebuffering and recoverable retries too. */
internal object PlaybackActivityPolicy {
    fun protectNetwork(
        hasChannel: Boolean,
        wantsToPlay: Boolean,
        ended: Boolean,
        fatalError: Boolean,
        remote: Boolean,
    ): Boolean = remote || (hasChannel && wantsToPlay && !ended && !fatalError)
}
