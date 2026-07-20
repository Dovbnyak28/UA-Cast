package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleChannelGuardTest {

    @Test
    fun `a stream that matches the active channel is current`() {
        assertTrue(StaleChannelGuard.isCurrent("https://origin/a.m3u8", "https://origin/a.m3u8"))
    }

    @Test
    fun `a stream the user has since zapped away from is stale`() {
        assertFalse(StaleChannelGuard.isCurrent("https://origin/a.m3u8", "https://origin/b.m3u8"))
    }

    @Test
    fun `no active channel at all means nothing is current`() {
        assertFalse(StaleChannelGuard.isCurrent("https://origin/a.m3u8", null))
    }
}
