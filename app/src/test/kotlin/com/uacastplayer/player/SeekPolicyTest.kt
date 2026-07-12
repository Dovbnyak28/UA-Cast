package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPolicyTest {

    @Test
    fun `allows seeking for non-live seekable local content`() {
        assertTrue(SeekPolicy.canSeek(isLive = false, isSeekable = true, isCasting = false))
    }

    @Test
    fun `forbids seeking live content`() {
        assertFalse(SeekPolicy.canSeek(isLive = true, isSeekable = true, isCasting = false))
    }

    @Test
    fun `forbids seeking non-seekable content`() {
        assertFalse(SeekPolicy.canSeek(isLive = false, isSeekable = false, isCasting = false))
    }

    @Test
    fun `forbids seeking while casting even if otherwise seekable`() {
        assertFalse(SeekPolicy.canSeek(isLive = false, isSeekable = true, isCasting = true))
    }

    @Test
    fun `forbids seeking when every condition is unfavorable`() {
        assertFalse(SeekPolicy.canSeek(isLive = true, isSeekable = false, isCasting = true))
    }
}
