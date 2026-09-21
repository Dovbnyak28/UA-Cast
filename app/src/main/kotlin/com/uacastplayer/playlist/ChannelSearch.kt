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

    // Only the QUERY is normalized into a string - once per search. Channel names are matched
    // against it in place by containsNormalized(), never normalized into strings of their own.
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun search(groups: List<GroupedChannels>, query: String): ChannelSearchOutcome {
        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.isEmpty()) return ChannelSearchOutcome.Matches(emptyList())
        val results = mutableListOf<ChannelSearchResult>()
        var truncated = false

        for (grouped in groups) {
            if (appendMatches(grouped, normalizedQuery, results)) {
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
        normalizedQuery: String,
        results: MutableList<ChannelSearchResult>,
    ): Boolean {
        for (channel in grouped.channels) {
            if (matches(channel, normalizedQuery)) {
                if (results.size == MAX_RESULTS) return true
                results += ChannelSearchResult(channel, grouped.group)
            }
        }
        return false
    }

    private fun matches(channel: M3uChannel, normalizedQuery: String): Boolean =
        containsNormalized(channel.displayName, normalizedQuery) ||
            channel.tvgName?.let { containsNormalized(it, normalizedQuery) } == true

    /** Collapses runs of whitespace to a single space, trims and lowercases, so "  HBO   Max " and
     * "hbo max" match the same way regardless of how a provider formatted the playlist. Applied to
     * the query only - see [containsNormalized] for why the channel names don't need it. */
    private fun normalizeQuery(value: String): String =
        value.trim().replace(WHITESPACE_REGEX, " ").lowercase()

    /**
     * True when [source] *normalized* contains [normalizedQuery] - without ever building that
     * normalized string.
     *
     * The straightforward version normalized every channel name into a fresh String and called
     * [String.contains] on it. That ran twice per channel (display name, tvg-name) for every
     * channel in the playlist on every search: on a 10k-channel playlist, ~40k throwaway Strings
     * per query, since a name with a space always matches the whitespace regex and a name with a
     * capital always allocates in `lowercase()`. This walks [source] a character at a time instead
     * and allocates nothing.
     *
     * Trimming [source] is skipped rather than reproduced: [normalizedQuery] is itself trimmed, so
     * it neither starts nor ends with a space, and a leading/trailing space on the source side can
     * therefore change no `contains` result either way.
     *
     * One deliberate divergence from the old behaviour: case folding is per character
     * ([Char.lowercaseChar]) rather than [String.lowercase]'s context-sensitive full mapping. They
     * differ only for Greek final sigma and a few dotted-I forms - i.e. only when the *query*
     * contains one - which is a fair trade here for dropping the allocations.
     */
    private fun containsNormalized(source: String, normalizedQuery: String): Boolean {
        // Every source index is tried as a start, including ones inside a whitespace run: those
        // just re-test the single space that run collapses to, which costs a comparison and keeps
        // the index arithmetic honest.
        for (start in source.indices) {
            if (matchesAt(source, start, normalizedQuery)) return true
        }
        return false
    }

    /** Whether the normalized form of `source[from..]` starts with [normalizedQuery]. Running out
     * of source mid-query ends the loop with characters still unconsumed, which is why the result
     * is "the query was fully consumed" rather than simply "the loop finished". */
    private fun matchesAt(source: String, from: Int, normalizedQuery: String): Boolean {
        var sourceIndex = from
        var queryIndex = 0
        while (queryIndex < normalizedQuery.length && sourceIndex < source.length) {
            sourceIndex = consume(source, sourceIndex, normalizedQuery[queryIndex])
            if (sourceIndex == NO_MATCH) return false
            queryIndex++
        }
        return queryIndex == normalizedQuery.length
    }

    /** Consumes as much of [source] at [sourceIndex] as the single [queryChar] accounts for,
     * returning the next source index - or [NO_MATCH] if it doesn't match. A whole run of
     * whitespace is one space in the normalized form, and [normalizedQuery] is normalized too, so
     * its whitespace is always exactly that single space. */
    private fun consume(source: String, sourceIndex: Int, queryChar: Char): Int {
        val sourceChar = source[sourceIndex]
        return if (sourceChar.isWhitespace()) {
            if (queryChar == ' ') skipWhitespace(source, sourceIndex) else NO_MATCH
        } else {
            if (sourceChar.lowercaseChar() == queryChar) sourceIndex + 1 else NO_MATCH
        }
    }

    private fun skipWhitespace(source: String, from: Int): Int {
        var index = from
        while (index < source.length && source[index].isWhitespace()) index++
        return index
    }

    private const val NO_MATCH = -1
}
