package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.ChannelSearch
import com.uacastplayer.playlist.ChannelSearchOutcome
import com.uacastplayer.playlist.ChannelListFilter
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel

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
