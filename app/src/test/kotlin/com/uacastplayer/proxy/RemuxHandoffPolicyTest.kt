package com.uacastplayer.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemuxHandoffPolicyTest {

    @Test
    fun `a confirmed new load kills the draining session immediately regardless of elapsed time`() {
        assertTrue(RemuxHandoffPolicy.shouldKillDraining(confirmed = true, elapsedMillis = 0))
    }

    @Test
    fun `an unconfirmed session keeps draining before the timeout`() {
        assertFalse(RemuxHandoffPolicy.shouldKillDraining(confirmed = false, elapsedMillis = 9_999))
    }

    @Test
    fun `an unconfirmed session is killed once the timeout elapses`() {
        val elapsed = RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS
        assertTrue(RemuxHandoffPolicy.shouldKillDraining(confirmed = false, elapsedMillis = elapsed))
    }

    @Test
    fun `an unconfirmed session past the timeout is still killed`() {
        assertTrue(RemuxHandoffPolicy.shouldKillDraining(confirmed = false, elapsedMillis = 20_000))
    }
}
