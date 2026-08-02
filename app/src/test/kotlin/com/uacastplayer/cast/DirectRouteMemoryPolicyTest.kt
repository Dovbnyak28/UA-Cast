package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectRouteMemoryPolicyTest {

    @Test
    fun `the proxy playing what direct could not is proof worth remembering`() {
        assertTrue(DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Proxy, ReceiverStatus.PLAYING))
    }

    /** The whole point of the bar: a direct attempt can fail because the origin blinked or the
     * network dropped, and the proxy still buffering proves nothing about the route. */
    @Test
    fun `the proxy merely buffering is not yet proof`() {
        assertFalse(DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Proxy, ReceiverStatus.BUFFERING))
    }

    @Test
    fun `the proxy going idle is not proof`() {
        assertFalse(DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Proxy, ReceiverStatus.IDLE))
    }

    @Test
    fun `the proxy pausing is not proof`() {
        assertFalse(DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Proxy, ReceiverStatus.PAUSED))
    }

    /** Direct playing is the opposite of what this records - it means direct works for this pair,
     * and nothing should be persisted that would skip it next time. */
    @Test
    fun `direct playing is never remembered as needing the proxy`() {
        assertFalse(DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Direct, ReceiverStatus.PLAYING))
    }

    @Test
    fun `direct in any other state is not remembered either`() {
        for (status in ReceiverStatus.entries) {
            assertFalse(
                "Direct mode should never be recorded, saw $status",
                DirectRouteMemoryPolicy.provenProxyOnly(CastDeliveryMode.Direct, status),
            )
        }
    }
}
