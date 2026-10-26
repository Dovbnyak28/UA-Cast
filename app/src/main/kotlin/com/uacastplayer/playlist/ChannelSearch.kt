package com.uacastplayer.playlist

/** One [M3uChannel] found by [ChannelSearch], paired with the [ChannelGroup] it belongs to so the
 * UI can show which group a match is in - useful once results span the whole playlist rather
 * than a single already-open group. */
data class ChannelSearchResult(val channel: M3uChannel, val group: ChannelGroup)

sealed interface ChannelSearchOutcome {
    data class Matches(val results: List<ChannelSearchResult>) : ChannelSearchOutcome

    /** Truncated to [ChannelSearch.MAX_RESULTS] - the caller shows a "refine your search" hint
     * alongside these instead of silently presenting a partial list as if it were complete. */
    data class TooBroad(val results: List<ChannelSearchResult>) : ChannelSearchOutcome
}

/**
 * Whole-playlist channel search, for playlists too large to browse group by group. Matches are a
 * case-insensitive substring check against [M3uChannel.displayName] and [M3uChannel.tvgName], in
 * playlist order (group order, then channel order within each group) rather than relevance-ranked
 * - predictable ordering matters more than ranking for an IPTV list a user already knows.
 */
object ChannelSearch {

    const val MAX_RESULTS = 200

    // Normalize and compile only the query, once per search; names are scanned without copies.
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun search(
        groups: List<GroupedChannels>,
        query: String,
        checkCancellation: () -> Unit = {},
    ): ChannelSearchOutcome {
        checkCancellation()
        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.isEmpty()) return ChannelSearchOutcome.Matches(emptyList())
        val matcher = NormalizedChannelQuery(normalizedQuery, checkCancellation)
        val results = mutableListOf<ChannelSearchResult>()
        var truncated = false

        for (grouped in groups) {
            checkCancellation()
            if (appendMatches(grouped, matcher, results, checkCancellation)) {
                truncated = true
                break
            }
        }

        return if (truncated) ChannelSearchOutcome.TooBroad(results) else ChannelSearchOutcome.Matches(results)
    }

    /** Adds matches in playlist order and reports whether there was at least one more result than
     * the bounded output can hold. The extra match is observed but never added. */
    private fun appendMatches(
        grouped: GroupedChannels,
        matcher: NormalizedChannelQuery,
        results: MutableList<ChannelSearchResult>,
        checkCancellation: () -> Unit,
    ): Boolean {
        for ((index, channel) in grouped.channels.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL_CHANNELS == 0) checkCancellation()
            if (matches(channel, matcher, checkCancellation)) {
                if (results.size == MAX_RESULTS) return true
                results += ChannelSearchResult(channel, grouped.group)
            }
        }
        return false
    }

    private fun matches(channel: M3uChannel, matcher: NormalizedChannelQuery, checkCancellation: () -> Unit): Boolean =
        matcher.contains(channel.displayName, checkCancellation) ||
            channel.tvgName?.let { matcher.contains(it, checkCancellation) } == true

    /** Collapses runs of whitespace to a single space, trims and lowercases, so "  HBO   Max " and
     * "hbo max" match the same way regardless of how a provider formatted the playlist. Applied to
     * the query only - see [NormalizedChannelQuery] for the allocation-free name scan. */
    private fun normalizeQuery(value: String): String =
        value.trim().replace(WHITESPACE_REGEX, " ").lowercase()

    private const val CANCELLATION_CHECK_INTERVAL_CHANNELS = 256
}
