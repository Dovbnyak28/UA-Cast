package com.uacastplayer.playlist

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks down the existing character-folding policy, rather than String.lowercase on names. */
class ChannelSearchEquivalenceTest {
    @Test fun `streaming search agrees with normalized reference across randomized names`() {
        val random = Random(731)
        repeat(4_000) { iteration ->
            val name = randomText(random, random.nextInt(0, 64))
            val alias = randomText(random, random.nextInt(0, 64))
            val query = when (iteration % 3) {
                0 -> name.take(random.nextInt(0, name.length + 1)).takeLast(12)
                1 -> alias.take(random.nextInt(0, alias.length + 1)).takeLast(12)
                else -> randomText(random, random.nextInt(0, 16))
            }
            val normalizedQuery = query.trim().replace(Regex("\\s+"), " ").lowercase()
            val expected = normalizedQuery.isNotEmpty() &&
                (normalizeName(name).contains(normalizedQuery) || normalizeName(alias).contains(normalizedQuery))
            assertEquals(
                "iteration=$iteration name=$name alias=$alias query=$query", expected, matches(name, query, alias),
            )
        }
    }

    @Test fun `overlapping prefixes recover the later match without skipping characters`() {
        assertEquals(true, matches("ababababac", "ababac"))
        assertEquals(false, matches("ababababab", "ababac"))
        assertEquals(true, matches("aaaabaaaab", "aaab"))
    }

    @Test fun `whitespace collapse preserves overlapping multiword matches`() {
        assertEquals(true, matches("ab  ab\tab\nac", "ab ab ac"))
        assertEquals(false, matches("ab  ab\tab\nac", "ab ab ad"))
        assertEquals(true, matches("Sport\u00a0\u00a0HD", "sport hd"))
        assertEquals(false, matches("Sport\u00a0HD", "sport\u00a0hd"))
    }

    @Test fun `character folding keeps existing sigma and dotted I behavior`() {
        for (name in listOf("ΟΣ", "Ος", "İ", "i\u0307", "Новини", "Ñandú")) {
            for (query in listOf("οσ", "ος", "İ", "i", "НОВИНИ", "ñANDÚ")) {
                val normalizedQuery = query.trim().replace(Regex("\\s+"), " ").lowercase()
                assertEquals(
                    "name=$name query=$query", normalizeName(name).contains(normalizedQuery), matches(name, query),
                )
            }
        }
    }

    private fun matches(name: String, query: String, alias: String? = null): Boolean {
        val channel = M3uChannel(name, "https://unused.example.test/live", tvgName = alias)
        val result = ChannelSearch.search(listOf(GroupedChannels(ChannelGroup.Custom("Test"), listOf(channel))), query)
        return (result as ChannelSearchOutcome.Matches).results.isNotEmpty()
    }

    private fun normalizeName(name: String): String = buildString {
        var whitespace = false
        for (char in name) {
            if (char.isWhitespace()) {
                if (!whitespace) append(' ')
                whitespace = true
            } else {
                append(char.lowercaseChar())
                whitespace = false
            }
        }
    }

    private fun randomText(random: Random, length: Int): String = buildString {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.size)]) }
    }

    private companion object {
        val ALPHABET = charArrayOf('a', 'A', 'b', ' ', '\t', '\n', '\u00a0', 'І', 'σ', 'Σ', 'İ', '\u0307', 'ñ')
    }
}
