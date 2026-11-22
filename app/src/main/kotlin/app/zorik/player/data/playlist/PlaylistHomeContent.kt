package app.zorik.player.data.playlist

import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.home.HomeContent
import app.zorik.player.home.HomeContentPolicy
import app.zorik.player.playlist.M3uChannel

/** Retires obsolete Home scans promptly so new playlist work can use the bounded CPU lane. */
internal suspend fun resolveHomeContent(
    lastWatchedChannelKey: String,
    channels: List<M3uChannel>,
    favorites: List<FavoriteChannel>,
): HomeContent = withPlaylistCpuCancellable { checkCancellation ->
    HomeContentPolicy.resolve(lastWatchedChannelKey, channels, favorites, checkCancellation)
}
