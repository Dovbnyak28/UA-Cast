package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesPlaylistPositionsTest {

    private fun channel(name: String, id: String? = name) = M3uChannel(
        displayName = name,
        streamUrl = "https://example.com/$name",
        tvgId = id,
    )

    @Test
    fun `only requested favorite keys are retained`() {
        val channels = listOf(channel("A"), channel("B"), channel("C"), channel("D"))

        val positions = FavoritesPlaylistPositions.resolve(channels, setOf("B", "D", "missing"))

        assertEquals(mapOf("B" to 1, "D" to 3), positions)
    }

    @Test
    fun `the first playlist position wins for a duplicate stable key`() {
        val channels = listOf(channel("First", id = "same"), channel("Second", id = "same"))

        val positions = FavoritesPlaylistPositions.resolve(channels, setOf("same"))

        assertEquals(mapOf("same" to 0), positions)
    }

    @Test
    fun `an empty favorite set allocates no positions`() {
        val positions = FavoritesPlaylistPositions.resolve(listOf(channel("A")), emptySet())

        assertEquals(emptyMap<String, Int>(), positions)
    }
}
