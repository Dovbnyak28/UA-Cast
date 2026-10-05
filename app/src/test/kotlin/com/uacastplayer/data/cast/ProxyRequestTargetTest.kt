package com.uacastplayer.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyRequestTargetTest {
    @Test
    fun `resource target accepts query without including it in id`() {
        assertEquals(
            ProxyRequestTarget.Resource("resource-id"),
            ProxyRequestTarget.parse("/hls/session-token/resource-id?cache=1", "session-token"),
        )
    }

    @Test
    fun `segment target retains the segment name`() {
        assertEquals(
            ProxyRequestTarget.RemuxSegment("resource-id", "seg42.ts"),
            ProxyRequestTarget.parse("/hls/session-token/resource-id/seg42.ts", "session-token"),
        )
    }

    @Test
    fun `wrong token and ambiguous paths are rejected`() {
        val invalid = listOf(
            "/hls/wrong/resource-id",
            "/hls/session-token",
            "/hls/session-token/resource-id/segment/extra",
            "/hls//session-token/resource-id",
            "hls/session-token/resource-id",
            "/other/session-token/resource-id",
        )

        invalid.forEach { path -> assertNull(path, ProxyRequestTarget.parse(path, "session-token")) }
    }
}
