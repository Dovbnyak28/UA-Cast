package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CastProxySessionTokenPolicyTest {

    @Test
    fun `same session keeps token across resume`() {
        val session = Any()

        val token = CastProxySessionTokenPolicy.select(session, session, "session-token") {
            "unexpected-new-token"
        }

        assertEquals("session-token", token)
    }

    @Test
    fun `new session gets a fresh token`() {
        val first = Any()
        val second = Any()

        val token = CastProxySessionTokenPolicy.select(first, second, "old-token") {
            "new-token"
        }

        assertEquals("new-token", token)
        assertNotEquals("old-token", token)
    }

    @Test
    fun `empty token is initialized even when there is no previous session`() {
        val token = CastProxySessionTokenPolicy.select(null, Any(), "") { "initialized" }

        assertEquals("initialized", token)
    }
}
