package com.uacastplayer.favorites

import java.util.Locale

/**
 * A "just enough" JSON reader/writer for a flat array-of-objects-of-strings-or-null shape - the
 * only shape the favorites cache needs. Written by hand instead of using org.json specifically
 * because org.json's Android implementation is stubbed out in local unit tests (it works fine on
 * a real device, but every call throws or no-ops under Robolectric-less `testDebugUnitTest`).
 */
object MiniJson {

    fun writeArrayOfObjects(objects: List<Map<String, String?>>): String =
        objects.joinToString(prefix = "[", postfix = "]", separator = ",") { writeObject(it) }

    private fun writeObject(fields: Map<String, String?>): String =
        fields.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            "${writeString(key)}:${if (value == null) "null" else writeString(value)}"
        }

    private fun writeString(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                // Locale.ROOT: this output is machine-read back by parseArrayOfObjects, so it must
                // always use ASCII digits regardless of the device's default locale (Arabic-indic,
                // Bengali, etc. digit systems would otherwise silently corrupt the escape).
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(Locale.ROOT, c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun parseArrayOfObjects(json: String): List<Map<String, String?>> = Parser(json).parseArray()

    private class Parser(private val text: String) {
        private var pos = 0

        fun parseArray(): List<Map<String, String?>> {
            skipWhitespace()
            expect('[')
            val result = mutableListOf<Map<String, String?>>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += parseObject()
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++; skipWhitespace() }
                    ']' -> { pos++; break }
                    else -> parseError("Expected ',' or ']'")
                }
            }
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
                if (pos >= text.length) parseError("Unterminated string")
                when (val c = text[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) parseError("Unterminated escape")
                        when (val escaped = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) parseError("Truncated unicode escape")
                                sb.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(escaped)
                        }
                    }
                    else -> sb.append(c)
                }
            }
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
