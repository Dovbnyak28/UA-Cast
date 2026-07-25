package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSessionIdTest {

    @Test
    fun `session id is the fixed, deliberately non-unique value`() {
        assertEquals("uacast_player", PLAYER_SESSION_ID)
    }
}
