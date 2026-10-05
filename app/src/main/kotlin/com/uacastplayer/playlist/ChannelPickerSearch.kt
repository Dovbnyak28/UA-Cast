package com.uacastplayer.playlist

/** Indices stay relative to the supplied (possibly parentally filtered) playback session. */
internal data class ChannelPickerMatches(val positions: List<Int>, val scrollPosition: Int)

internal object ChannelPickerSearch {
    fun search(
        channels: List<M3uChannel>,
        query: String,
        currentChannel: M3uChannel?,
        checkCancellation: () -> Unit = {},
    ): ChannelPickerMatches {
        val normalizedQuery = query.trim()
        val positions = ArrayList<Int>()
        var scrollPosition = 0
        channels.forEachIndexed { index, channel ->
            checkCancellation()
            if (normalizedQuery.isEmpty() || channel.displayName.contains(normalizedQuery, ignoreCase = true)) {
                if (normalizedQuery.isEmpty() && channel == currentChannel) scrollPosition = positions.size
                positions.add(index)
            }
        }
        return ChannelPickerMatches(positions, scrollPosition)
    }
}
