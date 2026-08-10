package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to a playing channel when the world interrupts it.
 *
 * Written as a table over the real-world events rather than over the parameters, because the
 * parameters are not the interesting part - the events are, and each of them arrives as a
 * different combination of the same three facts.
 */
class BackgroundPlaybackPolicyTest {

    private fun onStop(
        casting: Boolean = false,
        playing: Boolean = true,
        pip: Boolean = false,
    ) = BackgroundPlaybackPolicy.shouldPauseOnStop(casting, playing, pip)

    /**
     * The regression this policy exists for: Home, Recent Apps, app switching, screen lock - every
     * one of them stops the activity, and every one of them used to leave an IPTV stream running
     * with a wake lock held and no notification to stop it with.
     */
    @Test
    fun leavingTheAppWhilePlayingStopsPlayback() {
        assertTrue("Home", onStop())
        assertTrue("Recent Apps / app switch", onStop())
        assertTrue("screen lock", onStop())
    }

    /**
     * Picture-in-picture is the one way this app is meant to keep playing off its own screen, and
     * the window is right there in front of the user. Android does not deliver ON_STOP while a PiP
     * window is visible, so this covers the teardown ordering - where a wrong answer would pause a
     * video the user is still watching.
     */
    @Test
    fun pictureInPictureKeepsPlaying() {
        assertFalse(onStop(pip = true))
    }

    /**
     * Casting and then pocketing the phone is how a cast is normally used. The local player is
     * already stopped while a receiver plays (see LocalPlaybackPolicy), and the receiver is not
     * this app's to pause - `CastProxyService` is a foreground service precisely so that leaving
     * the app does not interrupt the TV.
     */
    @Test
    fun castingIsNotInterruptedByLeavingTheApp() {
        assertFalse("Chromecast", onStop(casting = true))
        assertFalse("DLNA", onStop(casting = true))
        assertFalse("casting while the phone reports it is playing", onStop(casting = true, playing = true))
    }

    /** Already paused - by the user, by an incoming call, by audio focus lost to another app.
     * There is nothing to stop, and claiming otherwise would make the app resume it later. */
    @Test
    fun somethingAlreadyPausedIsLeftAlone() {
        assertFalse("user pressed pause", onStop(playing = false))
        assertFalse("paused for an incoming call", onStop(playing = false))
        assertFalse("buffering or errored, not playing", onStop(playing = false))
    }

    /**
     * The rule that makes screen lock and unlock invisible: coming back resumes only what this
     * policy stopped.
     *
     * Without it, returning to the app would restart a channel the user had deliberately paused -
     * which is worse than the bug being fixed, because it happens on every single return rather
     * than only when the app is backgrounded.
     */
    @Test
    fun returningResumesOnlyWhatThisPolicyPaused() {
        assertTrue("unlock after a lock that paused us", BackgroundPlaybackPolicy.shouldResumeOnStart(true, false))
        assertFalse("the user paused it themselves", BackgroundPlaybackPolicy.shouldResumeOnStart(false, false))
    }

    /** A cast that started while the app was away owns playback now; the local player must stay
     * out of it, whatever it was doing before. */
    @Test
    fun nothingResumesLocallyWhileCasting() {
        assertFalse(BackgroundPlaybackPolicy.shouldResumeOnStart(pausedByPolicy = true, isCasting = true))
    }

    /** Pausing and resuming have to be exact opposites for the states that round-trip, or the app
     * ends up in a state no single event produced - playing when it should not be, or silent when
     * the user is looking at it. */
    @Test
    fun everyStateThatPausesIsAStateThatResumes() {
        for (casting in listOf(false, true)) {
            for (playing in listOf(false, true)) {
                for (pip in listOf(false, true)) {
                    val paused = BackgroundPlaybackPolicy.shouldPauseOnStop(casting, playing, pip)
                    val resumed = BackgroundPlaybackPolicy.shouldResumeOnStart(paused, casting)

                    assertTrue(
                        "casting=$casting playing=$playing pip=$pip paused=$paused resumed=$resumed",
                        paused == resumed,
                    )
                }
            }
        }
    }
}
