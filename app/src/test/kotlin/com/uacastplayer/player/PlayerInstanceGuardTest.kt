package com.uacastplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInstanceGuardTest {

    @Test
    fun `zero live instances is not a leak`() {
        assertFalse(PlayerInstanceGuard.isLeak(0))
    }

    @Test
    fun `exactly one live instance is the healthy steady state`() {
        assertFalse(PlayerInstanceGuard.isLeak(1))
    }

    @Test
    fun `two live instances is a leak`() {
        assertTrue(PlayerInstanceGuard.isLeak(2))
    }

    @Test
    fun `more than two live instances is a leak`() {
        assertTrue(PlayerInstanceGuard.isLeak(5))
    }
}
