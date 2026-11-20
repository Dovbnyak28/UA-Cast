package com.uacastplayer.data.playlist

import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.home.HomeContent
import com.uacastplayer.home.HomeContentPolicy
import com.uacastplayer.playlist.M3uChannel

/** Retires obsolete Home scans promptly so new playlist work can use the bounded CPU lane. */
internal suspend fun resolveHomeContent(
    lastWatchedChannelKey: String,
    channels: List<M3uChannel>,
    favorites: List<FavoriteChannel>,
): HomeContent = withPlaylistCpuCancellable { checkCancellation ->
    HomeContentPolicy.resolve(lastWatchedChannelKey, channels, favorites, checkCancellation)
}
