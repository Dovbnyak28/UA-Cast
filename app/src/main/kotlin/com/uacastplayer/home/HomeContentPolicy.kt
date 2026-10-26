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
        checkCancellation: () -> Unit = {},
    ): HomeContent {
        checkCancellation()
        val continueWatching = lastWatchedChannelKey?.let { key -> findLastWatched(channels, key, checkCancellation) }
        return HomeContent(
            continueWatching = continueWatching,
            favorites = favorites.take(MAX_FAVORITES_SHOWN),
        )
    }

    private fun findLastWatched(
        channels: List<M3uChannel>,
        key: String,
        checkCancellation: () -> Unit,
    ): M3uChannel? {
        for ((index, channel) in channels.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL_CHANNELS == 0) checkCancellation()
            if (FavoriteKey.matches(channel, key)) return channel
        }
        return null
    }

    private const val CANCELLATION_CHECK_INTERVAL_CHANNELS = 256
}
