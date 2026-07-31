package com.uacastplayer.player

/**
 * Whether the local [androidx.media3.exoplayer.ExoPlayer] should prepare (buffer/decode) the
 * channel that was just set as its current media item. While a cast session is active, the
 * receiver already handles playback of that same stream - preparing it locally too would have
 * the phone buffering/decoding it a second time in parallel, for no one to watch, until the
 * PauseLocalPlayer side effect catches up.
 */
object LocalPlaybackPolicy {
    fun shouldPrepareLocally(isCasting: Boolean): Boolean = !isCasting
}
