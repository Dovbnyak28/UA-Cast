package com.uacastplayer.data.prefs

/** PLACEHOLDERS never shows a real logo; CACHE behaves normally (network + cache); CACHE_LIMITED only ever shows what's already cached. */
enum class IconDisplayMode {
    PLACEHOLDERS, CACHE, CACHE_LIMITED;

    companion object {
        val DEFAULT = CACHE
        fun fromId(id: String?): IconDisplayMode = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

enum class ListDensity {
    FULL, SIMPLE, MINIMAL;

    companion object {
        val DEFAULT = FULL
        fun fromId(id: String?): ListDensity = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

enum class ChannelLayout {
    LIST, GRID, LARGE_ICONS;

    companion object {
        val DEFAULT = GRID
        fun fromId(id: String?): ChannelLayout = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/** How the Favorites screen orders its channels - see `com.uacastplayer.favorites.FavoritesSorter`.
 * MANUAL means the stored [com.uacastplayer.favorites.FavoriteChannel] list order itself is the
 * order shown - see `com.uacastplayer.favorites.ReorderPolicy` for the drag-to-reorder math. */
enum class FavoritesSortOrder {
    PLAYLIST_ORDER, ALPHABETICAL, RECENTLY_ADDED, MANUAL;

    companion object {
        val DEFAULT = PLAYLIST_ORDER
        fun fromId(id: String?): FavoritesSortOrder = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/**
 * ExoPlayer buffer duration preset - see `com.uacastplayer.player.PlayerRenderersFactoryProvider`
 * (or wherever the LoadControl is built) for the actual millisecond values. SMALL favors fast
 * channel switching on stable connections; LARGE trades startup/switch latency for resilience to
 * unstable or slow networks.
 */
enum class BufferSize {
    SMALL, MEDIUM, LARGE;

    companion object {
        val DEFAULT = MEDIUM
        fun fromId(id: String?): BufferSize = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/** Maps to `androidx.media3.ui.AspectRatioFrameLayout`'s RESIZE_MODE_* constants - kept as its own
 * enum (rather than storing the raw int) so it round-trips through [AppPreferences] the same way
 * every other setting here does, and so the mapping to/from the Media3 constant lives in exactly
 * one place (see `player.ResizeModeCycle`). Global, not per-channel - matches how every other
 * player preset in this file behaves. */
enum class PlayerResizeMode {
    FIT, FILL, ZOOM;

    companion object {
        val DEFAULT = FIT
        fun fromId(id: String?): PlayerResizeMode = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
