package com.uacastplayer.proxy

import java.io.IOException

/** Structural admission shared by unwrap, flatten and rewrite, before any list/URI allocation. */
internal object HlsPlaylistBudget {
    const val MAX_LINES = 100_000
    const val MAX_REFERENCES = 20_000
    private val lineEnds = charArrayOf('\r', '\n')

    fun accepts(text: String): Boolean {
        if (text.length > MAX_HLS_PLAYLIST_BYTES) return false
        var start = 0
        var lines = 0
        var references = 0
        while (start <= text.length && lines <= MAX_LINES && references <= MAX_REFERENCES) {
            lines++
            val end = text.indexOfAny(lineEnds, start).let { if (it < 0) text.length else it }
            if (isReference(text, start, end)) references++
            start = nextLineStart(text, end)
        }
        return lines <= MAX_LINES && references <= MAX_REFERENCES
    }

    private fun isReference(text: String, start: Int, end: Int): Boolean {
        var first = start
        if (first == 0 && text.startsWith(UTF8_BOM)) first++
        while (first < end && text[first].isWhitespace()) first++
        return first < end && text[first] != '#'
    }

    private fun nextLineStart(text: String, end: Int): Int {
        val next = end + 1
        return if (next < text.length && text[end] == '\r' && text[next] == '\n') next + 1 else next
    }

    fun requireAccepted(text: String) {
        if (!accepts(text)) throw IOException("HLS structural budget exceeded")
    }
}
