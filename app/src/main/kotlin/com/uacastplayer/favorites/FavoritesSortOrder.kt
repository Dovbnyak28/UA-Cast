package com.uacastplayer.favorites

/**
 * How the Favorites screen orders its channels.
 *
 * [MANUAL] means the stored [FavoriteChannel] list order itself is the order shown; see
 * [ReorderPolicy] for the drag-to-reorder math.
 */
enum class FavoritesSortOrder {
    PLAYLIST_ORDER, ALPHABETICAL, RECENTLY_ADDED, MANUAL;

    companion object {
        val DEFAULT = PLAYLIST_ORDER
        fun fromId(id: String?): FavoritesSortOrder = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
