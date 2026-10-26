package com.uacastplayer.core.settings

/** Controls whether logos use network plus cache, cache only, or placeholders only. */
enum class IconDisplayMode {
    PLACEHOLDERS, CACHE, CACHE_LIMITED;

    companion object {
        val DEFAULT = CACHE
        fun fromId(id: String?): IconDisplayMode = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/** Amount of channel metadata shown in a list row. */
enum class ListDensity {
    FULL, SIMPLE, MINIMAL;

    companion object {
        val DEFAULT = FULL
        fun fromId(id: String?): ListDensity = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/** Primary presentation used for channel groups. */
enum class ChannelLayout {
    LIST, GRID, LARGE_ICONS;

    companion object {
        val DEFAULT = GRID
        fun fromId(id: String?): ChannelLayout = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/**
 * ExoPlayer buffer duration preset. SMALL favors fast switching on stable connections; LARGE
 * trades startup/switch latency for resilience to unstable or slow networks.
 */
enum class BufferSize {
    SMALL, MEDIUM, LARGE;

    companion object {
        val DEFAULT = MEDIUM
        fun fromId(id: String?): BufferSize = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/**
 * Video fit/fill/zoom preset. The player adapter owns the mapping to Media3 constants, while this
 * dependency-free value can be persisted and reported without either layer depending on the other.
 */
enum class PlayerResizeMode {
    FIT, FILL, ZOOM;

    companion object {
        val DEFAULT = FIT
        fun fromId(id: String?): PlayerResizeMode = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
