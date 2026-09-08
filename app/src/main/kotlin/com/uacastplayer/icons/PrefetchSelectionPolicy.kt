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
        checkCancellation: () -> Unit = {},
    ): List<M3uChannel> {
        if (limit <= 0) return emptyList()

        checkCancellation()
        val favorites = if (priority.favoriteKeys.isEmpty()) emptySequence() else channels.asSequence().filter {
            checkCancellation()
            FavoriteKey.of(it) in priority.favoriteKeys
        }
        // Lazy categories avoid even scanning last-watched once favorites fill the budget.
        val lastWatched = sequence {
            priority.lastWatchedKey?.let { key ->
                channels.firstOrNull {
                    checkCancellation()
                    FavoriteKey.of(it) == key
                }?.let { yield(it) }
            }
        }
        val seenKeys = HashSet<String>()
        return (favorites + lastWatched + priority.firstGroupChannels.asSequence())
            .filter { checkCancellation(); seenKeys.add(FavoriteKey.of(it)) }
            .filterNot(isCached)
            .take(limit)
            .toList()
    }
}
