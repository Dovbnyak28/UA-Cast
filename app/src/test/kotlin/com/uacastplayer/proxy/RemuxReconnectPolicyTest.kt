package com.uacastplayer.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class RemuxReconnectPolicyTest {

    @Test
    fun `backs off 1s then 2s then 4s across three attempts`() {
        val first = RemuxReconnectPolicy.onDisconnected(0)
        check(first is RemuxReconnectPolicy.Decision.Retry)
        assertEquals(1_000L, first.delayMillis)
        assertEquals(1, first.nextAttempt)

        val second = RemuxReconnectPolicy.onDisconnected(first.nextAttempt)
        check(second is RemuxReconnectPolicy.Decision.Retry)
        assertEquals(2_000L, second.delayMillis)
        assertEquals(2, second.nextAttempt)

        val third = RemuxReconnectPolicy.onDisconnected(second.nextAttempt)
        check(third is RemuxReconnectPolicy.Decision.Retry)
        assertEquals(4_000L, third.delayMillis)
        assertEquals(3, third.nextAttempt)
    }

    @Test
    fun `gives up after three consecutive failed attempts`() {
        assertEquals(RemuxReconnectPolicy.Decision.GiveUp, RemuxReconnectPolicy.onDisconnected(3))
    }

    @Test
    fun `a fresh disconnect after attempt count was reset starts the backoff over`() {
        val decision = RemuxReconnectPolicy.onDisconnected(0)
        check(decision is RemuxReconnectPolicy.Decision.Retry)
        assertEquals(1_000L, decision.delayMillis)
    }
}
