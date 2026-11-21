package com.uacastplayer.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpHeaderValuePolicyTest {

    @Test
    fun `keeps a valid bounded header value`() {
        assertEquals("Mozilla/5.0", HttpHeaderValuePolicy.sanitize(" Mozilla/5.0 "))
    }

    @Test
    fun `rejects header injection and control characters`() {
        assertNull(HttpHeaderValuePolicy.sanitize("safe\r\nX-Injected: yes"))
        assertNull(HttpHeaderValuePolicy.sanitize("value\u0000suffix"))
        assertNull(HttpHeaderValuePolicy.sanitize("value\u007fsuffix"))
    }

    @Test
    fun `rejects values over the per-header limit`() {
        assertNull(HttpHeaderValuePolicy.sanitize("x".repeat(HttpHeaderValuePolicy.MAX_VALUE_LENGTH + 1)))
    }

    @Test
    fun `invalid user agent falls back to the shared browser default`() {
        assertEquals(
            HttpDefaults.BROWSER_USER_AGENT,
            HttpHeaderValuePolicy.userAgentOrDefault("UA\r\nInjected: yes"),
        )
    }
}
