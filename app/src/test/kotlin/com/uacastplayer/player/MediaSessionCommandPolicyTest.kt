package com.uacastplayer.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionCommandPolicyTest {

    @Test
    fun `maps seek to next media item to NEXT`() {
        assertEquals(
            MediaSessionCommandPolicy.Action.NEXT,
            MediaSessionCommandPolicy.mapCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        )
    }

    @Test
    fun `maps seek to previous media item to PREVIOUS`() {
        assertEquals(
            MediaSessionCommandPolicy.Action.PREVIOUS,
            MediaSessionCommandPolicy.mapCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
        )
    }

    @Test
    fun `unrelated commands are not mapped`() {
        assertNull(MediaSessionCommandPolicy.mapCommand(Player.COMMAND_PLAY_PAUSE))
        assertNull(MediaSessionCommandPolicy.mapCommand(Player.COMMAND_SEEK_TO_NEXT))
        assertNull(MediaSessionCommandPolicy.mapCommand(Player.COMMAND_SEEK_TO_PREVIOUS))
    }
}
