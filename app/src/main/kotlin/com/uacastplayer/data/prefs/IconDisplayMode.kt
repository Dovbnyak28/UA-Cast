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
        val DEFAULT = LIST
        fun fromId(id: String?): ChannelLayout = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
