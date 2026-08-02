package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All eight combinations of the three inputs, because the interesting cases here are the ones that
 * must *not* fall back and each has a different reason - a table beats picking a couple of examples.
 */
class DirectFailureFallbackPolicyTest {

    private fun shouldRetry(
        result: CastLoadResult,
        mode: CastDeliveryMode,
        incompatible: Boolean,
    ) = DirectFailureFallbackPolicy.shouldRetryOnProxy(result, mode, incompatible)

    private val failure = CastLoadResult.Failure("status_2100")

    @Test
    fun `a direct failure with no confirmed incompatibility is the one case that falls back`() {
        assertTrue(shouldRetry(failure, CastDeliveryMode.Direct, incompatible = false))
    }

    @Test
    fun `a confirmed incompatible codec blocks the fallback - the proxy remuxes containers, not video`() {
        assertFalse(shouldRetry(failure, CastDeliveryMode.Direct, incompatible = true))
    }

    @Test
    fun `a failure already on the proxy does not fall back to the proxy again`() {
        assertFalse(shouldRetry(failure, CastDeliveryMode.Proxy, incompatible = false))
        assertFalse(shouldRetry(failure, CastDeliveryMode.Proxy, incompatible = true))
    }

    /**
     * Success means the receiver *accepted* the media, not that it is playing it. The gap between
     * those two is real and is exactly what CastStallWatchdogPolicy covers; treating an accepted
     * load as a reason to switch routes would tear down a cast that is about to work.
     */
    @Test
    fun `a successful load never falls back, in either mode`() {
        for (mode in listOf(CastDeliveryMode.Direct, CastDeliveryMode.Proxy)) {
            for (incompatible in listOf(false, true)) {
                assertFalse(
                    "Success in $mode (incompatible=$incompatible) must not fall back",
                    shouldRetry(CastLoadResult.Success, mode, incompatible),
                )
            }
        }
    }
}
