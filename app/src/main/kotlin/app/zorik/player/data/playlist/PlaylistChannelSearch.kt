package app.zorik.player.data.playlist

import app.zorik.player.playlist.ChannelSearch
import app.zorik.player.playlist.ChannelSearchOutcome
import app.zorik.player.playlist.ChannelListFilter
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel

/** Cancels obsolete phone scans on the shared CPU lane without changing the matching policies. */
internal suspend fun searchPlaylistChannels(
    groups: List<GroupedChannels>,
    query: String,
): ChannelSearchOutcome = withPlaylistCpuCancellable { checkCancellation ->
    ChannelSearch.search(groups, query, checkCancellation)
}

internal suspend fun filterPlaylistChannels(
    channels: List<M3uChannel>,
    query: String,
): List<M3uChannel> = if (query.isEmpty()) {
    channels
} else {
    withPlaylistCpuCancellable { checkCancellation ->
        ChannelListFilter.filter(channels, emptyList(), emptySet(), query, checkCancellation)
    }
}
