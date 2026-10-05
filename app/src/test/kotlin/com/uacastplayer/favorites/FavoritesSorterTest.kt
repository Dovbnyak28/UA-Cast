package com.uacastplayer.favorites

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesSorterTest {

    private val ukrainian: Locale = Locale.forLanguageTag("uk")
    private val russian: Locale = Locale.forLanguageTag("ru")

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

    /**
     * The alphabet this app was written for. Sorting lowercased strings compares UTF-16 code units:
     * Ґ is U+0490, above я, and Є/І/Ї are U+0404-0407, in a block that also lands after я - so the
     * order came out Атлант, Ера, Ялта, Єдині, Інтер, Ґалас. The test above was the only one there
     * was, and it used English words, which is how this survived.
     */
    @Test
    fun `alphabetical sort follows the Ukrainian alphabet, not UTF-16 order`() {
        val favorites = listOf("Ялта", "Ґалас", "Інтер", "Атлант", "Єдині новини", "Ера").map(::favorite)

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.ALPHABETICAL, ukrainian) { null }

        assertEquals(
            listOf("Атлант", "Ґалас", "Ера", "Єдині новини", "Інтер", "Ялта"),
            sorted.map { it.displayName },
        )
    }

    /** Russian ships too, and has its own outlier: ё belongs beside е, not after я. */
    @Test
    fun `alphabetical sort places yo beside ye in Russian`() {
        val favorites = listOf("Ясень", "Ёлка", "Если", "Дом").map(::favorite)

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.ALPHABETICAL, russian) { null }

        assertEquals(listOf("Дом", "Ёлка", "Если", "Ясень"), sorted.map { it.displayName })
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

    @Test
    fun `manual order returns the stored list order unchanged`() {
        val favorites = listOf(favorite("c"), favorite("a"), favorite("b"))

        val sorted = FavoritesSorter.sort(favorites, FavoritesSortOrder.MANUAL) { null }

        assertEquals(listOf("c", "a", "b"), sorted.map { it.displayName })
    }
}
