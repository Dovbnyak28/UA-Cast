package com.uacastplayer.favorites

import com.uacastplayer.data.prefs.FavoritesSortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesSorterTest {

    private fun favorite(name: String, addedAtMillis: Long = 0L) =
        FavoriteChannel(
            key = name,
            displayName = name,
            streamUrl = "http://x/$name",
            tvgId = null,
            groupTitle = null,
            addedAtMillis = addedAtMillis,
        )

    @Test
    fun `alphabetical sort is case-insensitive`() {
        val favorites = listOf(favorite("banana"), favorite("Apple"), favorite("cherry"))

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.ALPHABETICAL) { null }

        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.displayName })
    }

    @Test
    fun `recently added sorts newest first`() {
        val favorites = listOf(favorite("old", 1_000L), favorite("newest", 3_000L), favorite("mid", 2_000L))

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.RECENTLY_ADDED) { null }

        assertEquals(listOf("newest", "mid", "old"), sorted.map { it.displayName })
    }

    @Test
    fun `playlist order follows the lookup`() {
        val favorites = listOf(favorite("c"), favorite("a"), favorite("b"))
        val playlistIndex = mapOf("a" to 0, "b" to 1, "c" to 2)

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.PLAYLIST_ORDER) { playlistIndex[it.key] }

        assertEquals(listOf("a", "b", "c"), sorted.map { it.displayName })
    }

    @Test
    fun `playlist order sorts favorites missing from the playlist last`() {
        val favorites = listOf(favorite("gone"), favorite("present"))
        val playlistIndex = mapOf("present" to 0)

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.PLAYLIST_ORDER) { playlistIndex[it.key] }

        assertEquals(listOf("present", "gone"), sorted.map { it.displayName })
    }
}
