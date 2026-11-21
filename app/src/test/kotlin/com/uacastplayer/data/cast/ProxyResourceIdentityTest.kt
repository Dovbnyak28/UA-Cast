package com.uacastplayer.data.cast

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProxyResourceIdentityTest {
    @Test fun `same URL with different access headers cannot overwrite a prior resource`() {
        val registry = ProxyResourceRegistry(OkHttpClient())
        val a = registry.registerPlaylist("https://x/live", "Agent A", "https://a/")
        val b = registry.registerPlaylist("https://x/live", "Agent B", "https://b/")
        assertNotEquals(a, b)
        assertEquals("Agent A", registry.get(a)!!.userAgent)
        assertEquals("https://a/", registry.get(a)!!.referrer)
        assertEquals(a, registry.registerPlaylist("https://x/live", "Agent A", "https://a/"))
    }
}
