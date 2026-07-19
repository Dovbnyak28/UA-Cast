package com.uacastplayer.core.net

import org.junit.Assert.assertSame
import org.junit.Test

class AppHttpTest {

    @Test
    fun `derived clients share one connection pool and dispatcher`() {
        val a = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)
        val b = AppHttp.client(connectTimeoutSeconds = 15, readTimeoutSeconds = 60)

        assertSame(a.connectionPool, b.connectionPool)
        assertSame(a.dispatcher, b.dispatcher)
    }
}
