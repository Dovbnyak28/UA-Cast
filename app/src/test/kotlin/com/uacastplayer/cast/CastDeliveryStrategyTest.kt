package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastDeliveryStrategyTest {

    @Test
    fun `initial mode is Direct when not known incompatible`() {
        assertEquals(CastDeliveryMode.Direct, CastDeliveryStrategy.initialMode(isKnownIncompatible = false))
    }

    @Test
    fun `initial mode is Proxy when known incompatible`() {
        assertEquals(CastDeliveryMode.Proxy, CastDeliveryStrategy.initialMode(isKnownIncompatible = true))
    }

    @Test
    fun `direct failure falls back to proxy`() {
        assertEquals(CastDeliveryMode.Proxy, CastDeliveryStrategy.onDirectFailure(CastDeliveryMode.Direct))
    }

    @Test
    fun `a failure while already on proxy stays on proxy`() {
        assertEquals(CastDeliveryMode.Proxy, CastDeliveryStrategy.onDirectFailure(CastDeliveryMode.Proxy))
    }

    @Test
    fun `watchdog timeout with non-PLAYING status falls back to proxy`() {
        val result = CastDeliveryStrategy.onWatchdogTimeout(CastDeliveryMode.Direct, ReceiverStatus.BUFFERING)
        assertEquals(CastDeliveryMode.Proxy, result)
    }

    @Test
    fun `watchdog timeout with PLAYING status stays on direct`() {
        val result = CastDeliveryStrategy.onWatchdogTimeout(CastDeliveryMode.Direct, ReceiverStatus.PLAYING)
        assertEquals(CastDeliveryMode.Direct, result)
    }

    @Test
    fun `watchdog timeout while already on proxy is a no-op`() {
        val result = CastDeliveryStrategy.onWatchdogTimeout(CastDeliveryMode.Proxy, ReceiverStatus.IDLE)
        assertEquals(CastDeliveryMode.Proxy, result)
    }

    @Test
    fun `proxy mode is terminal, direct mode is not`() {
        assertTrue(CastDeliveryStrategy.isTerminalFailure(CastDeliveryMode.Proxy))
        assertFalse(CastDeliveryStrategy.isTerminalFailure(CastDeliveryMode.Direct))
    }
}
