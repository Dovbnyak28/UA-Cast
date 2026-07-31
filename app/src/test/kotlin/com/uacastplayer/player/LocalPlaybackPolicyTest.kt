package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackPolicyTest {

    @Test
    fun `prepares locally when not casting`() {
        assertTrue(LocalPlaybackPolicy.shouldPrepareLocally(isCasting = false))
    }

    @Test
    fun `does not prepare locally while casting`() {
        assertFalse(LocalPlaybackPolicy.shouldPrepareLocally(isCasting = true))
    }
}
