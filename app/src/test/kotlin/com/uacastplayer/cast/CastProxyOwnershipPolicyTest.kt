package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProxyOwnershipPolicyTest {

    @Test
    fun `stopping DLNA leaves Chromecast protected`() {
        val both = CastProxyOwnershipPolicy.started(
            CastProxyOwnershipPolicy.started(CastProxyOwnership(), CastProxyTarget.CHROMECAST),
            CastProxyTarget.DLNA,
        )

        val remaining = CastProxyOwnershipPolicy.stopped(both, CastProxyTarget.DLNA)

        assertEquals(listOf(CastProxyTarget.CHROMECAST), remaining.activeTargets)
        assertEquals(CastProxyTarget.CHROMECAST, remaining.displayedTarget)
    }

    @Test
    fun `stopping Chromecast leaves DLNA protected`() {
        val both = CastProxyOwnershipPolicy.started(
            CastProxyOwnershipPolicy.started(CastProxyOwnership(), CastProxyTarget.CHROMECAST),
            CastProxyTarget.DLNA,
        )

        val remaining = CastProxyOwnershipPolicy.stopped(both, CastProxyTarget.CHROMECAST)

        assertEquals(listOf(CastProxyTarget.DLNA), remaining.activeTargets)
        assertEquals(CastProxyTarget.DLNA, remaining.displayedTarget)
    }

    @Test
    fun `restarting one target creates no duplicate and makes it the displayed owner`() {
        val both = CastProxyOwnershipPolicy.started(
            CastProxyOwnershipPolicy.started(CastProxyOwnership(), CastProxyTarget.CHROMECAST),
            CastProxyTarget.DLNA,
        )

        val restarted = CastProxyOwnershipPolicy.started(both, CastProxyTarget.CHROMECAST)

        assertEquals(
            listOf(CastProxyTarget.DLNA, CastProxyTarget.CHROMECAST),
            restarted.activeTargets,
        )
        assertEquals(CastProxyTarget.CHROMECAST, restarted.displayedTarget)
        assertTrue(restarted.activeTargets.distinct().size == restarted.activeTargets.size)
    }
}
