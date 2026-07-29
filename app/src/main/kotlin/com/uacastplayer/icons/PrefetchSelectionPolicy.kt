package com.uacastplayer.icons

import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel

/**
 * Picks which channels the background icon prefetch should actually fetch this pass, instead of
 * [com.uacastplayer.data.icons.IconPrefetcher] blindly queuing every channel in the playlist. On a
 * large playlist (thousands of channels) fetching all of them competes with playback/scroll for
 * minutes after every load - this narrows the pass to what's actually likely to be seen soon:
 * favorites, the last-watched channel, and the first group (the one Home/Channels shows by
 * default), capped at [limit] total. Anything not selected here still gets its icon the lazy way,
 * on demand, the first time its row is actually composed (see ChannelIcon's resolveIcon call).
 */
object PrefetchSelectionPolicy {

    /** [firstGroupChannels] is the already-grouped channel list for whichever group displays first
     * (see ChannelGrouper) - passed directly rather than re-matched by title, since a raw M3U
     * group-title string doesn't necessarily equal the normalized [com.uacastplayer.playlist.ChannelGroup]
     * it was bucketed under. */
    data class PriorityChannels(
        val favoriteKeys: Set<String> = emptySet(),
        val lastWatchedKey: String? = null,
        val firstGroupChannels: List<M3uChannel> = emptyList(),
    )

    fun select(
        channels: List<M3uChannel>,
        priority: PriorityChannels,
        limit: Int,
        isCached: (M3uChannel) -> Boolean = { false },
    ): List<M3uChannel> {
        if (limit <= 0) return emptyList()

        val lastWatched = priority.lastWatchedKey
            ?.let { key -> channels.firstOrNull { FavoriteKey.of(it) == key } }
        // Priority order: favorites, then the last-watched channel, then the first group - a channel
        // appearing in more than one category is only fetched once, thanks to the dedupe below.
        val ordered = channels.filter { FavoriteKey.of(it) in priority.favoriteKeys } +
            listOfNotNull(lastWatched) +
            priority.firstGroupChannels

        val seenKeys = HashSet<String>()
        return ordered
            .filter { seenKeys.add(FavoriteKey.of(it)) }
            .filterNot(isCached)
            .take(limit)
    }
}
