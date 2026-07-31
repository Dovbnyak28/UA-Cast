package com.uacastplayer.playlist

/**
 * Stable LazyColumn/LazyVerticalGrid item key for a channel at a known list position.
 *
 * A bare `streamUrl` isn't safe as a key on its own: real playlists frequently repeat a URL
 * (mirrors, the same channel re-listed under multiple groups), and Lazy*'s `key` lambda throws
 * IllegalArgumentException the moment two items currently in the list share one. Prefixing with
 * the item's position guarantees uniqueness within a single list even when URLs collide. It does
 * mean the key changes if filtering/sorting shifts a channel's position, which only costs that
 * row a fresh recomposition instead of preserving its identity across the change - an acceptable
 * trade for never crashing on duplicate URLs.
 */
object ChannelListKeys {
    fun keyFor(index: Int, streamUrl: String): String = "$index:${streamUrl.hashCode()}"
}
