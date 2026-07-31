package com.uacastplayer.home

import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel

/** What Home's "continue watching" card and favorites row should show - worked out once so the
 * screen itself only renders, it doesn't decide. */
data class HomeContent(
    val continueWatching: M3uChannel?,
    val favorites: List<FavoriteChannel>,
)

object HomeContentPolicy {

    const val MAX_FAVORITES_SHOWN = 10

    /**
     * [lastWatchedChannelKey] resolves against [channels] by [FavoriteKey] - the same identifier
     * favorites use, not a raw URL. A key that no longer matches anything in the current playlist
     * (channel removed, or the playlist was replaced entirely) silently drops the card rather
     * than showing an entry that can't actually play.
     */
    fun resolve(
        lastWatchedChannelKey: String?,
        channels: List<M3uChannel>,
        favorites: List<FavoriteChannel>,
    ): HomeContent {
        val continueWatching = lastWatchedChannelKey?.let { key -> channels.firstOrNull { FavoriteKey.of(it) == key } }
        return HomeContent(
            continueWatching = continueWatching,
            favorites = favorites.take(MAX_FAVORITES_SHOWN),
        )
    }
}
