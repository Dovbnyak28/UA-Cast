package com.uacastplayer.favorites

import com.uacastplayer.data.prefs.FavoritesSortOrder

/**
 * Pure ordering for the Favorites screen. [PLAYLIST_ORDER][FavoritesSortOrder.PLAYLIST_ORDER]
 * needs external context (where each favorite currently sits in the loaded playlist) since that
 * isn't part of [FavoriteChannel] itself; favorites no longer present in the playlist sort last,
 * in their existing relative order, rather than disappearing or jumping around.
 */
object FavoritesSorter {
    fun sort(
        favorites: List<FavoriteChannel>,
        order: FavoritesSortOrder,
        playlistIndexOf: (FavoriteChannel) -> Int?,
    ): List<FavoriteChannel> = when (order) {
        FavoritesSortOrder.PLAYLIST_ORDER -> favorites.sortedBy { playlistIndexOf(it) ?: Int.MAX_VALUE }
        FavoritesSortOrder.ALPHABETICAL -> favorites.sortedBy { it.displayName.lowercase() }
        FavoritesSortOrder.RECENTLY_ADDED -> favorites.sortedByDescending { it.addedAtMillis }
    }
}
