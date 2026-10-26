package com.uacastplayer.core.json

import java.util.Locale

/**
 * A "just enough" JSON reader/writer for a flat array-of-objects-of-strings-or-null shape used by
 * the app's small persistence codecs. Written by hand instead of using org.json specifically
 * because org.json's Android implementation is stubbed out in local unit tests (it works fine on
 * a real device, but every call throws or no-ops under Robolectric-less `testDebugUnitTest`).
 */
object MiniJson {

    private const val FIRST_PRINTABLE_CODE_POINT = 0x20
    private const val UNICODE_ESCAPE_HEX_DIGITS = 4
    private const val HEX_RADIX = 16

    fun writeArrayOfObjects(objects: List<Map<String, String?>>): String =
        writeArrayOfObjects(objects) { it }

    /** Builds each record directly into the result rather than materializing a second mapped list. */
    fun <T> writeArrayOfObjects(objects: Iterable<T>, fieldsFor: (T) -> Map<String, String?>): String =
        buildString {
            append('[')
            var first = true
            for (item in objects) {
                if (!first) append(',')
                first = false
                appendObject(fieldsFor(item))
            }
            append(']')
        }

    private fun StringBuilder.appendObject(fields: Map<String, String?>) {
        append('{')
        var first = true
        for ((key, value) in fields) {
            if (!first) append(',')
            first = false
            appendJsonString(key)
            append(':')
            if (value == null) append("null") else appendJsonString(value)
        }
        append('}')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                // Locale.ROOT: this output is machine-read back by parseArrayOfObjects, so it must
                // always use ASCII digits regardless of the device's default locale (Arabic-indic,
                // Bengali, etc. digit systems would otherwise silently corrupt the escape).
                else -> if (c.code < FIRST_PRINTABLE_CODE_POINT) {
                    append("\\u%04x".format(Locale.ROOT, c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    fun parseArrayOfObjects(json: String): List<Map<String, String?>> =
        parseArrayOfObjects(json) { it }

    /** Transforms each parsed object immediately so callers need not retain a second list of maps. */
    fun <T : Any> parseArrayOfObjects(json: String, transform: (Map<String, String?>) -> T?): List<T> =
        Parser(json).parseArray(transform)

    private class Parser(private val text: String) {
        private var pos = 0

        fun <T : Any> parseArray(transform: (Map<String, String?>) -> T?): List<T> {
            skipWhitespace()
            expect('[')
            val result = mutableListOf<T>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
            } else {
                while (true) {
                    transform(parseObject())?.let(result::add)
                    skipWhitespace()
                    when (peek()) {
                        ',' -> { pos++; skipWhitespace() }
                        ']' -> { pos++; break }
                        else -> parseError("Expected ',' or ']'")
                    }
                }
            }
            skipWhitespace()
            if (pos != text.length) parseError("Unexpected trailing content")
            return result
        }

        private fun parseObject(): Map<String, String?> {
            skipWhitespace()
            expect('{')
            val map = mutableMapOf<String, String?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; break }
                    else -> parseError("Expected ',' or '}'")
                }
            }
            return map
        }

        private fun parseValue(): String? {
            skipWhitespace()
            if (peek() == 'n') {
                expectLiteral("null")
                return null
            }
            return parseString()
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val character = nextCharacter("Unterminated string")
                if (character == '"') return sb.toString()
                when {
                    character == '\\' -> appendEscapedCharacter(sb)
                    character.code < FIRST_PRINTABLE_CODE_POINT -> parseError("Unescaped control character")
                    else -> sb.append(character)
                }
            }
        }

        private fun appendEscapedCharacter(target: StringBuilder) {
            when (val escaped = nextCharacter("Unterminated escape")) {
                '"' -> target.append('"')
                '\\' -> target.append('\\')
                '/' -> target.append('/')
                'n' -> target.append('\n')
                'r' -> target.append('\r')
                't' -> target.append('\t')
                'u' -> appendUnicodeEscape(target)
                else -> parseError("Unsupported escape '$escaped'")
            }
        }

        private fun appendUnicodeEscape(target: StringBuilder) {
            if (pos + UNICODE_ESCAPE_HEX_DIGITS > text.length) {
                parseError("Truncated unicode escape")
            }
            val code = text.substring(pos, pos + UNICODE_ESCAPE_HEX_DIGITS).toIntOrNull(HEX_RADIX)
                ?: parseError("Malformed unicode escape")
            target.append(code.toChar())
            pos += UNICODE_ESCAPE_HEX_DIGITS
        }

        private fun nextCharacter(errorMessage: String): Char {
            if (pos >= text.length) parseError(errorMessage)
            return text[pos++]
        }

        private fun peek(): Char {
            if (pos >= text.length) parseError("Unexpected end of input")
            return text[pos]
        }

        private fun expect(c: Char) {
            if (peek() != c) parseError("Expected '$c'")
            pos++
        }

        private fun expectLiteral(literal: String) {
            if (!text.startsWith(literal, pos)) parseError("Expected '$literal'")
            pos += literal.length
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun parseError(message: String): Nothing = throw IllegalArgumentException("$message at position $pos")
    }
}
