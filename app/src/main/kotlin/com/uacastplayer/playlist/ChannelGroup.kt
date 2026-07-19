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

// Prefixed per subtype so a provider's raw custom group title can never collide with a Known
// group's key constant or the Ungrouped sentinel - e.g. a real playlist with a literal custom
// group titled "ungrouped" would otherwise produce the same key as ChannelGroup.Ungrouped and
// crash the LazyVerticalGrid the first time both were visible together. Also the stable identity
// GroupVisibilityStore/GroupOrderPolicy persist pin/hide state against - see docs there.
fun groupDisplayKey(group: ChannelGroup): String = when (group) {
    is ChannelGroup.Known -> "known:${group.key}"
    is ChannelGroup.Custom -> "custom:${group.rawTitle}"
    ChannelGroup.Ungrouped -> "ungrouped"
}
