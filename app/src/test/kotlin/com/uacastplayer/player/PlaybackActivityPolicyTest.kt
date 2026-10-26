package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActivityPolicyTest {
    @Test fun `preparing buffering and retrying protect network even without rendered frames`() {
        assertTrue(PlaybackActivityPolicy.protectNetwork(true, true, false, false, false))
    }

    @Test fun `pause close end and fatal failure release speculative work`() {
        assertFalse(PlaybackActivityPolicy.protectNetwork(true, false, false, false, false))
        assertFalse(PlaybackActivityPolicy.protectNetwork(false, true, false, false, false))
        assertFalse(PlaybackActivityPolicy.protectNetwork(true, true, true, false, false))
        assertFalse(PlaybackActivityPolicy.protectNetwork(true, true, false, true, false))
    }

    @Test fun `remote session keeps priority independently of the stopped local player`() {
        assertTrue(PlaybackActivityPolicy.protectNetwork(false, false, true, true, true))
    }
}
