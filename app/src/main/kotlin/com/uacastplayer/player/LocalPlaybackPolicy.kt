package com.uacastplayer.player

/**
 * Whether the local [androidx.media3.exoplayer.ExoPlayer] should prepare (buffer/decode) the
 * channel that was just set as its current media item. While a cast session is active, the
 * receiver already handles playback of that same stream - preparing it locally too would have
 * the phone buffering/decoding it a second time in parallel, for no one to watch, until the
 * PauseLocalPlayer side effect catches up.
 *
 * [isRemoteCasting], not "is Chromecast casting": a DLNA renderer
 * (`com.uacastplayer.dlna.DlnaSessionRepository`) is playing the same stream through the same
 * proxy, so it starves on a second local connection exactly the way a Chromecast receiver does -
 * IPTV origins routinely allow one connection per account. See `PlayerViewModel.isRemoteCasting`.
 */
object LocalPlaybackPolicy {
    fun shouldPrepareLocally(isRemoteCasting: Boolean): Boolean = !isRemoteCasting

    /**
     * Whether disconnecting one remote target should hand playback back to the local player.
     *
     * There are two remote targets and they are disconnected independently, so "the cast ended"
     * does not mean "nothing is playing remotely". Resuming unconditionally is how the phone ends
     * up playing the same stream as a still-connected receiver: audible twice, and - because an
     * origin routinely allows one connection per account - the phone takes the slot and the
     * receiver starves. That is the exact failure [shouldPrepareLocally] exists to prevent, applied
     * to the other end of the session.
     *
     * Both arguments are passed explicitly rather than read from one combined flag because the two
     * targets report through separate flows with no ordering between them: when Chromecast's
     * "resume local" effect arrives, its own connected flag may not have been cleared yet, so the
     * caller says `isChromecastActive = false` itself rather than asking a value that is about to
     * change.
     */
    fun shouldResumeAfterDisconnect(isChromecastActive: Boolean, isDlnaActive: Boolean): Boolean =
        !isChromecastActive && !isDlnaActive
}
