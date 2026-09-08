package com.uacastplayer

import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlayerRequestSaverTest {

    @Test
    fun openRequestProducesCompactMarkerForTheSelectedChannel() {
        val channels = listOf(
            M3uChannel("First", "https://example.invalid/first.m3u8"),
            M3uChannel("Second", "https://example.invalid/second.m3u8"),
        )
        assertEquals(
            SavedPlayerRequest(FavoriteKey.of(channels[1]), 1),
            PlayerRequest(channels, 1).toSavedRequest(),
        )
    }

    @Test
    fun invalidOpenRequestCannotLeaveARestorableMarker() {
        val channel = M3uChannel("First", "https://example.invalid/first.m3u8")
        assertNull(PlayerRequest(emptyList(), 0).toSavedRequest())
        assertNull(PlayerRequest(listOf(channel), -1).toSavedRequest())
        assertNull(PlayerRequest(listOf(channel), 1).toSavedRequest())
    }

    @Test
    fun restoresValidSavedRequest() {
        assertEquals(
            SavedPlayerRequest("channel-42", 7),
            SavedPlayerRequestSaver.restore(listOf("channel-42", 7)),
        )
    }

    @Test
    fun persistedChannelKeyWinsAfterNavigation() {
        val saved = SavedPlayerRequest("original", 0)

        assertEquals("latest", saved.preferredChannelKey("latest"))
        assertEquals("original", saved.preferredChannelKey(null))
    }

    @Test
    fun emptyStateMeansNoPendingRequest() {
        assertNull(SavedPlayerRequestSaver.restore(emptyList()))
    }

    @Test
    fun malformedStateIsDiscardedInsteadOfCrashing() {
        val malformed = listOf<List<Any>>(
            listOf("only-a-key"),
            listOf(42, 7),
            listOf("channel-42", "seven"),
            listOf("", 0),
            listOf("channel-42", -1),
        )

        malformed.forEach { saved ->
            assertNull("malformed saved state must be ignored: $saved", SavedPlayerRequestSaver.restore(saved))
        }
    }
}
