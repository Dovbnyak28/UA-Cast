package com.uacastplayer.diagnostics

import com.uacastplayer.core.cast.CastRouteKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRouteAttemptTrackerTest {

    @Test
    fun `manifest polls dedupe but replaying the same resource is a new attempt`() {
        val tracker = ProxyRouteAttemptTracker()

        assertTrue(tracker.markIfNew(1, "stable-sha", CastRouteKind.PROXY_REWRITE))
        assertFalse(tracker.markIfNew(1, "stable-sha", CastRouteKind.PROXY_REWRITE))
        assertTrue(tracker.markIfNew(2, "stable-sha", CastRouteKind.PROXY_REWRITE))
    }

    @Test
    fun `a route change for one resource is represented in both route buckets`() {
        val tracker = ProxyRouteAttemptTracker()

        assertTrue(tracker.markIfNew(7, "resource", CastRouteKind.PROXY_REWRITE))
        assertTrue(tracker.markIfNew(7, "resource", CastRouteKind.PROXY_REMUX))
    }

    @Test
    fun `clear starts a fresh session`() {
        val tracker = ProxyRouteAttemptTracker()
        assertTrue(tracker.markIfNew(1, "resource", CastRouteKind.PROXY_REWRITE))
        tracker.clear()
        assertTrue(tracker.markIfNew(1, "resource", CastRouteKind.PROXY_REWRITE))
    }
}
