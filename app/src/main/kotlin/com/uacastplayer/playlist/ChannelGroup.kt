package com.uacastplayer.playlist

/**
 * Result of normalizing a raw `group-title` value. [Known] groups map to a stable, translatable
 * key the UI layer resolves against string resources; [Custom] preserves a provider's own group
 * name verbatim (it isn't one of our recognized categories, but it's still real data worth
 * keeping); [Ungrouped] is the fallback for channels with no group at all.
 */
sealed class ChannelGroup {
    data class Known(val key: String) : ChannelGroup()
    data class Custom(val rawTitle: String) : ChannelGroup()
    data object Ungrouped : ChannelGroup()

    companion object {
        const val KEY_MOVIES = "movies"
        const val KEY_SERIES = "series"
        const val KEY_NEWS = "news"
        const val KEY_SPORTS = "sports"
        const val KEY_KIDS = "kids"
        const val KEY_MUSIC = "music"
        const val KEY_DOCUMENTARY = "documentary"
        const val KEY_ENTERTAINMENT = "entertainment"
        const val KEY_SCIENCE = "science"
        const val KEY_RELIGION = "religion"
        const val KEY_REGIONAL = "regional"
    }
}
