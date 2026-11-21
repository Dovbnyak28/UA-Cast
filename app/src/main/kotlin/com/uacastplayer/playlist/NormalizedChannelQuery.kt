package com.uacastplayer.playlist

/**
 * A KMP prefix table shared by every name in one search. Each name is scanned in O(length),
 * including repeated near-matches, without allocating a normalized copy. Only the query needs
 * O(query.length) extra memory; no channel objects are cached or retained after the search.
 *
 * Keep the existing policy: source whitespace runs become one space and case folding uses
 * Char.lowercaseChar, not String.lowercase's context-sensitive/full Unicode mappings. Since
 * the query is already trimmed, trimming the source cannot affect a match.
 */
internal class NormalizedChannelQuery(private val query: String, checkCancellation: () -> Unit) {
    private val prefixLengths = IntArray(query.length)

    init {
        require(query.isNotEmpty())
        var matched = 0
        for (index in 1 until query.length) {
            if (index % CANCELLATION_CHECK_INTERVAL_CHARS == 0) checkCancellation()
            matched = nextMatchedLength(matched, query[index], checkCancellation)
            prefixLengths[index] = matched
        }
    }

    // Collapsing whitespace and folding individual characters cannot make a name longer.
    fun contains(source: String, checkCancellation: () -> Unit): Boolean =
        source.length >= query.length && scan(source, checkCancellation)

    private fun scan(source: String, checkCancellation: () -> Unit): Boolean {
        var matched = 0
        var previousWhitespace = false
        for ((index, rawChar) in source.withIndex()) {
            if (source.length >= CANCELLATION_CHECK_INTERVAL_CHARS &&
                index % CANCELLATION_CHECK_INTERVAL_CHARS == 0) checkCancellation()
            val whitespace = rawChar.isWhitespace()
            if (whitespace && previousWhitespace) continue
            previousWhitespace = whitespace
            val char = if (whitespace) ' ' else rawChar.lowercaseChar()
            matched = nextMatchedLength(matched, char, checkCancellation)
            if (matched == query.length) return true
        }
        return false
    }

    private fun nextMatchedLength(initial: Int, char: Char, checkCancellation: () -> Unit): Int {
        var matched = initial
        var fallbacks = 0
        while (matched > 0 && query[matched] != char) {
            // A single fallback can walk a long prefix even though the total work is linear.
            if (++fallbacks % CANCELLATION_CHECK_INTERVAL_CHARS == 0) checkCancellation()
            matched = prefixLengths[matched - 1]
        }
        return if (query[matched] == char) matched + 1 else matched
    }

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL_CHARS = 1_024
    }
}
