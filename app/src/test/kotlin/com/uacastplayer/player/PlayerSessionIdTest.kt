package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerSessionIdTest {

    @Test
    fun `includes the counter value in the id`() {
        assertEquals("uacast_player_1", playerSessionId(1))
        assertEquals("uacast_player_42", playerSessionId(42))
    }

    @Test
    fun `different counters produce different ids`() {
        assertNotEquals(playerSessionId(1), playerSessionId(2))
    }

    @Test
    fun `is stable for the same counter`() {
        assertEquals(playerSessionId(7), playerSessionId(7))
    }
}
