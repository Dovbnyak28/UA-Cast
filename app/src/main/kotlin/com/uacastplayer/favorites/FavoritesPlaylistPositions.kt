package com.uacastplayer.favorites

import com.uacastplayer.playlist.M3uChannel

/**
 * Positions only the favorites that can affect Favorites' playlist-order sort.
 *
 * Building `FavoriteKey -> index` for every channel retained a 40,000-entry map to order a list
 * that usually contains tens of items. This scans the playlist in the same order, records only
 * requested keys, and stops as soon as every favorite has been found.
 */
object FavoritesPlaylistPositions {

    fun resolve(channels: List<M3uChannel>, favoriteKeys: Set<String>): Map<String, Int> {
        if (favoriteKeys.isEmpty() || channels.isEmpty()) return emptyMap()
        val unresolved = favoriteKeys.toMutableSet()
        val positions = HashMap<String, Int>(favoriteKeys.size)
        for ((index, channel) in channels.withIndex()) {
            val key = FavoriteKey.of(channel)
            if (unresolved.remove(key)) positions[key] = index
            if (unresolved.isEmpty()) break
        }
        return positions
    }
}
