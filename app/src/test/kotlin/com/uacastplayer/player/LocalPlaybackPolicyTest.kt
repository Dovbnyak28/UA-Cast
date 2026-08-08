package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression protection for a defect this app could produce with two taps: disconnecting one remote
 * target while the other was still connected handed playback back to the phone, so the same stream
 * played locally and on the receiver at once.
 *
 * The audible duplicate is the smaller half. An origin that allows one connection per account gives
 * the slot to whoever asks - the phone did - and the receiver, which was playing perfectly a second
 * earlier, starves. Both `PlayerViewModel` resume paths ran `prepare(); play()` without asking
 * whether anything else was still playing.
 */
class LocalPlaybackPolicyTest {

    @Test
    fun preparingLocallyIsSkippedWhileAnythingIsPlayingRemotely() {
        assertTrue(LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting = false))
        assertFalse(LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting = true))
    }

    @Test
    fun disconnectingTheOnlyRemoteTargetHandsPlaybackBack() {
        assertTrue(
            LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                isChromecastActive = false,
                isDlnaActive = false,
            ),
        )
    }

    /** The bug, from the Chromecast side: DLNA is still playing, so the phone must stay quiet. */
    @Test
    fun endingAChromecastSessionDoesNotResumeOverAConnectedDlnaRenderer() {
        assertFalse(
            LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                isChromecastActive = false,
                isDlnaActive = true,
            ),
        )
    }

    /** And from the DLNA side: the Chromecast receiver is still playing. */
    @Test
    fun disconnectingDlnaDoesNotResumeOverALiveChromecastSession() {
        assertFalse(
            LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                isChromecastActive = true,
                isDlnaActive = false,
            ),
        )
    }

    /** Both connected at once is not a state the UI offers, but the policy must not invent a
     * resume out of it either - a wrong answer here is double playback, the same as the two cases
     * above. */
    @Test
    fun bothRemoteTargetsActiveNeverResumesLocally() {
        assertFalse(
            LocalPlaybackPolicy.shouldResumeAfterDisconnect(
                isChromecastActive = true,
                isDlnaActive = true,
            ),
        )
    }
}
