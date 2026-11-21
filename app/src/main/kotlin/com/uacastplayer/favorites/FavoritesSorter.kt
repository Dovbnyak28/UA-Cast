package com.uacastplayer.favorites

import java.text.Collator
import java.util.Locale

/**
 * Pure ordering for the Favorites screen. [PLAYLIST_ORDER][FavoritesSortOrder.PLAYLIST_ORDER]
 * needs external context (where each favorite currently sits in the loaded playlist) since that
 * isn't part of [FavoriteChannel] itself; favorites no longer present in the playlist sort last,
 * in their existing relative order, rather than disappearing or jumping around.
 */
object FavoritesSorter {

    /**
     * @param locale whose alphabet "alphabetical" means. Taken as a parameter rather than read from
     *   the ambient default for the same reason [com.uacastplayer.epg.DayScheduleBuilder] takes a
     *   `ZoneId`: it is the one input that decides the answer, and a policy that reads it from the
     *   environment cannot be tested for it.
     */
    fun sort(
        favorites: List<FavoriteChannel>,
        order: FavoritesSortOrder,
        locale: Locale = Locale.getDefault(),
        playlistIndexOf: (FavoriteChannel) -> Int?,
    ): List<FavoriteChannel> = when (order) {
        FavoritesSortOrder.PLAYLIST_ORDER -> favorites.sortedBy { playlistIndexOf(it) ?: Int.MAX_VALUE }
        // A Collator, not `lowercase()`. Sorting lowercased strings compares UTF-16 code units,
        // which is not the Ukrainian alphabet and is not close to it: Ґ (U+0490) sits above я, so
        // it sorted dead last, and Є/І/Ї (U+0404-0407) sit in a separate block that lands after я
        // too. A Ukrainian user asking for alphabetical order got Атлант, Ера, Ялта, Єдині, Інтер,
        // Ґалас. The same applies to ё in Russian and to ñ in Spanish, both of which this ships in.
        FavoritesSortOrder.ALPHABETICAL -> favorites.sortedWith(
            compareBy(Collator.getInstance(locale)) { it.displayName },
        )
        FavoritesSortOrder.RECENTLY_ADDED -> favorites.sortedByDescending { it.addedAtMillis }
        FavoritesSortOrder.MANUAL -> favorites
    }
}
