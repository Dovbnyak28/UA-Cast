package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaybackRetryPolicyTest {

    @Test
    fun `a retry executes while local foreground playback is still wanted`() {
        assertEquals(
            LocalPlaybackRetryAction.PREPARE_NOW,
            decide(),
        )
    }

    @Test
    fun `a receiver taking ownership drops the stale local retry`() {
        assertEquals(
            LocalPlaybackRetryAction.DROP,
            decide(remote = true),
        )
    }

    @Test
    fun `a user pause drops the stale retry`() {
        assertEquals(
            LocalPlaybackRetryAction.DROP,
            decide(wantsToPlay = false),
        )
    }

    @Test
    fun `a wanted retry becomes foreground debt while the app is away`() {
        assertEquals(
            LocalPlaybackRetryAction.DEFER_UNTIL_FOREGROUND,
            decide(background = true),
        )
    }

    @Test
    fun `a lifecycle pause still represents wanted playback and is deferred`() {
        assertEquals(
            LocalPlaybackRetryAction.DEFER_UNTIL_FOREGROUND,
            decide(background = true, wantsToPlay = false, pausedForBackground = true),
        )
    }

    @Test
    fun `remote ownership wins even over foreground debt`() {
        assertEquals(
            LocalPlaybackRetryAction.DROP,
            decide(remote = true, background = true, pausedForBackground = true),
        )
    }

    private fun decide(
        remote: Boolean = false,
        background: Boolean = false,
        wantsToPlay: Boolean = true,
        pausedForBackground: Boolean = false,
    ): LocalPlaybackRetryAction = LocalPlaybackRetryPolicy.decide(
        isRemoteCasting = remote,
        isInBackground = background,
        wantsToPlay = wantsToPlay,
        pausedForBackground = pausedForBackground,
    )
}
