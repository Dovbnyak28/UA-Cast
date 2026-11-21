package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSessionIdentityGuardTest {
    @Test
    fun `accepts lifecycle event from current session instance`() {
        val session = Any()

        assertTrue(CastSessionIdentityGuard.isCurrent(session, session))
    }

    @Test
    fun `rejects event from previous session and event after teardown`() {
        val previous = Any()
        val current = Any()

        assertFalse(CastSessionIdentityGuard.isCurrent(previous, current))
        assertFalse(CastSessionIdentityGuard.isCurrent(previous, null))
    }
}
