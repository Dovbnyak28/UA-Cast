package com.uacastplayer.playlist

/**
 * Forward-only reader for M3U attributes and quoted/bare values. Keys start with an ASCII letter;
 * their continuation and whitespace retain Android regex's Unicode character semantics. An unanchored
 * greedy regex retries every suffix of a malformed key; consuming each token once bounds work
 * even when a provider sends a whole line without '='. Cancellation belongs to the caller.
 */
internal class M3uAttributeReader(
    private val content: String,
    start: Int,
    private val end: Int,
    private val checkCancellation: () -> Unit,
) {
    private var cursor = start
    private var nextCancellationCheck = start + CANCELLATION_CHECK_INTERVAL_CHARS

    fun forEach(consume: (key: String, value: String) -> Unit) {
        while (cursor < end) {
            advanceWhile { !it.isAsciiLetter() }
            val keyStart = cursor
            advanceWhile { it.isKeyCharacter() }
            val keyEnd = cursor
            if (cursor < end && content[cursor] == '=') {
                cursor++
                val value = readValue()
                if (value != null) consume(content.substring(keyStart, keyEnd), value)
            }
        }
    }

    private fun readValue(): String? {
        if (cursor == end || content.codePointAt(cursor).isAttributeWhitespace()) return null
        val start = cursor
        var value: String? = null
        if (content[cursor] == '"') {
            cursor++
            advanceWhile { it != '"'.code }
            if (cursor < end) {
                value = content.substring(start + 1, cursor)
                cursor++
            } else {
                // Preserve tolerant bare-value fallback for an unmatched opening quote. There
                // cannot be another quote in this suffix, so this extra scan is still linear.
                cursor = start
            }
        }
        if (value == null) {
            advanceWhile { !it.isAttributeWhitespace() }
            value = content.substring(start, cursor)
        }
        return value
    }

    private inline fun advanceWhile(predicate: (Int) -> Boolean) {
        while (cursor < end) {
            val codePoint = content.codePointAt(cursor)
            if (!predicate(codePoint)) break
            if (cursor >= nextCancellationCheck) {
                checkCancellation()
                nextCancellationCheck = cursor + CANCELLATION_CHECK_INTERVAL_CHARS
            }
            cursor += Character.charCount(codePoint)
        }
    }

    private fun Int.isAsciiLetter(): Boolean = this in 'a'.code..'z'.code || this in 'A'.code..'Z'.code

    private fun Int.isKeyCharacter(): Boolean = when {
        this < ASCII_LIMIT -> isAsciiLetter() || this in '0'.code..'9'.code || this == '_'.code || this == '-'.code
        Character.isAlphabetic(this) -> true
        this == '\u200C'.code || this == '\u200D'.code -> true // Unicode join controls.
        else -> when (Character.getType(this)) {
            Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(), Character.DECIMAL_DIGIT_NUMBER.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(), -> true
            else -> false
        }
    }

    private fun Int.isAttributeWhitespace(): Boolean =
        this == ' '.code || this in '\t'.code..'\r'.code || this == '\u0085'.code || Character.isSpaceChar(this)

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL_CHARS = 1_024
        const val ASCII_LIMIT = 128
    }
}
