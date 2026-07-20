package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompatibilityRecordingPolicyTest {

    @Test
    fun `a confirmed incompatible codec is always recorded, even if it somehow played`() {
        val result = IncompatibilityRecordingPolicy.shouldRecord(
            isConfirmedIncompatible = true,
            everReachedPlaying = true,
        )
        assertTrue(result)
    }

    @Test
    fun `a confirmed incompatible codec that never played is recorded`() {
        val result = IncompatibilityRecordingPolicy.shouldRecord(
            isConfirmedIncompatible = true,
            everReachedPlaying = false,
        )
        assertTrue(result)
    }

    @Test
    fun `a channel that never reached PLAYING at all is recorded - not a transient blip`() {
        val result = IncompatibilityRecordingPolicy.shouldRecord(
            isConfirmedIncompatible = false,
            everReachedPlaying = false,
        )
        assertTrue(result)
    }

    @Test
    fun `a channel that played at some point before failing is transient, not recorded`() {
        val result = IncompatibilityRecordingPolicy.shouldRecord(
            isConfirmedIncompatible = false,
            everReachedPlaying = true,
        )
        assertFalse(result)
    }
}
