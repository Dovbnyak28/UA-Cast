package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackPolicyTest {

    @Test
    fun `prepares locally when not casting`() {
        assertTrue(LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting = false))
    }

    /** "Remote", not "Chromecast" - a DLNA renderer is playing the same stream through the same
     * proxy, so a second local connection starves it identically. */
    @Test
    fun `does not prepare locally while casting to any remote target`() {
        assertFalse(LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting = true))
    }
}
