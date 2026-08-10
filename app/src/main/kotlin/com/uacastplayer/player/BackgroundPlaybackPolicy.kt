package com.uacastplayer.player

/**
 * Whether local playback should stop when the app leaves the screen, and start again when it comes
 * back.
 *
 * **This app does not do background playback, and until now it did it by accident.** There is no
 * `MediaSessionService` and no foreground service behind the local player - deliberately, since
 * this is a live-TV app and playback is not meant to outlive the player screen. But nothing ever
 * paused it either, so pressing Home from a player that was not in picture-in-picture left an
 * IPTV stream running from a stopped activity: several megabits a minute, a partial wake lock and
 * a Wi-Fi lock held by `WAKE_MODE_NETWORK`, audio out of the speaker, and no notification anywhere
 * to stop it with. The only way out was to reopen the app. On top of that the process is a cached
 * one by then, so the system is free to kill it mid-stream, which is how the same bug also reads
 * as "playback randomly stops in the background".
 *
 * Kept here as a pure function of what is true at the moment the lifecycle moves, because that is
 * the only form of it that can be tested: the alternative is asserting on a real `ExoPlayer` inside
 * a real Activity going through a real backgrounding.
 */
object BackgroundPlaybackPolicy {

    /**
     * Whether to pause as the app stops being visible.
     *
     * Three cases are left alone, and each for its own reason:
     *
     * - **Picture-in-picture.** The window is still on screen and the video in it is the entire
     *   point. Android does not deliver `ON_STOP` while a PiP window is visible, so this is a guard
     *   against the teardown ordering rather than the common path - but a wrong answer here would
     *   pause the video in a window the user is looking at.
     * - **Casting.** The local player is already stopped while a receiver is playing (see
     *   `LocalPlaybackPolicy`), and the receiver's playback is not this app's to pause. Casting and
     *   then putting the phone away is the normal way to use a cast.
     * - **Already paused.** Nothing to do, and saying otherwise would make [shouldResumeOnStart]
     *   resume something the user had deliberately stopped.
     */
    fun shouldPauseOnStop(isCasting: Boolean, isPlaying: Boolean, isInPictureInPicture: Boolean): Boolean =
        isPlaying && !isCasting && !isInPictureInPicture

    /**
     * Whether to start again as the app becomes visible - true only when *this* policy is what
     * stopped it.
     *
     * That distinction is the whole reason a flag is worth keeping. Without it, returning to the
     * app would restart a channel the user had paused on purpose; with it, locking the screen and
     * unlocking it a moment later is invisible, which is what a live-TV app should feel like. There
     * is no position to lose either way: resuming live TV means resuming live.
     */
    fun shouldResumeOnStart(pausedByPolicy: Boolean, isCasting: Boolean): Boolean =
        pausedByPolicy && !isCasting
}
