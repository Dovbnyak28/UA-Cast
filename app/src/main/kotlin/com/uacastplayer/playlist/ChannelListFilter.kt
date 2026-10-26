package com.uacastplayer.playlist

/** Display-name filtering without intermediate copies; cancellation releases the playlist CPU lane. */
object ChannelListFilter {
    fun filter(
        channels: List<M3uChannel>,
        groups: List<GroupedChannels>,
        hiddenGroupKeys: Set<String>,
        query: String,
        checkCancellation: () -> Unit = {},
    ): List<M3uChannel> {
        checkCancellation()
        val normalizedQuery = query.trim()
        if (hiddenGroupKeys.isEmpty() && normalizedQuery.isEmpty()) return channels
        val result = mutableListOf<M3uChannel>()
        if (hiddenGroupKeys.isEmpty()) {
            appendMatches(channels, normalizedQuery, result, checkCancellation)
        } else {
            for (group in groups) {
                checkCancellation()
                if (groupDisplayKey(group.group) !in hiddenGroupKeys) {
                    appendMatches(group.channels, normalizedQuery, result, checkCancellation)
                }
            }
        }
        return result
    }

    private fun appendMatches(
        channels: List<M3uChannel>,
        query: String,
        result: MutableList<M3uChannel>,
        checkCancellation: () -> Unit,
    ) {
        for ((index, channel) in channels.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0) checkCancellation()
            if (query.isEmpty() || channel.displayName.contains(query, ignoreCase = true)) result += channel
        }
    }

    private const val CANCELLATION_CHECK_INTERVAL = 256
}
