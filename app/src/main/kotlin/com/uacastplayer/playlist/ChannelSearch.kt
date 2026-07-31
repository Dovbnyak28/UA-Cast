package com.uacastplayer.playlist

/** One [M3uChannel] found by [ChannelSearch], paired with the [ChannelGroup] it belongs to so the
 * UI can show which group a match is in - useful once results span the whole playlist rather
 * than a single already-open group. */
data class ChannelSearchResult(val channel: M3uChannel, val group: ChannelGroup)

sealed class ChannelSearchOutcome {
    data class Matches(val results: List<ChannelSearchResult>) : ChannelSearchOutcome()

    /** Truncated to [ChannelSearch.MAX_RESULTS] - the caller shows a "refine your search" hint
     * alongside these instead of silently presenting a partial list as if it were complete. */
    data class TooBroad(val results: List<ChannelSearchResult>) : ChannelSearchOutcome()
}

/**
 * Whole-playlist channel search, for playlists too large to browse group by group. Matches are a
 * case-insensitive substring check against [M3uChannel.displayName] and [M3uChannel.tvgName], in
 * playlist order (group order, then channel order within each group) rather than relevance-ranked
 * - predictable ordering matters more than ranking for an IPTV list a user already knows.
 */
object ChannelSearch {

    const val MAX_RESULTS = 200

    fun search(groups: List<GroupedChannels>, query: String): ChannelSearchOutcome {
        val normalizedQuery = normalize(query)
        val results = mutableListOf<ChannelSearchResult>()
        var truncated = false

        if (normalizedQuery.isNotEmpty()) {
            outer@ for (grouped in groups) {
                for (channel in grouped.channels) {
                    if (!matches(channel, normalizedQuery)) continue
                    if (results.size == MAX_RESULTS) {
                        truncated = true
                        break@outer
                    }
                    results += ChannelSearchResult(channel, grouped.group)
                }
            }
        }

        return if (truncated) ChannelSearchOutcome.TooBroad(results) else ChannelSearchOutcome.Matches(results)
    }

    private fun matches(channel: M3uChannel, normalizedQuery: String): Boolean =
        normalize(channel.displayName).contains(normalizedQuery) ||
            channel.tvgName?.let { normalize(it).contains(normalizedQuery) } == true

    /** Collapses runs of whitespace to a single space and trims, so "  HBO   Max " and "hbo max"
     * match the same way regardless of how a provider formatted the playlist. */
    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()
}
