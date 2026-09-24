package com.uacastplayer.backup

/**
 * A cheap lexical admission check before [org.json.JSONObject] builds an in-memory tree.
 *
 * The import byte limit alone is not a memory limit: a few megabytes of tiny `{}` values can
 * expand into hundreds of thousands of JSONObject/Map instances, and deeply nested input can
 * exhaust the parser stack. This pass ignores punctuation inside quoted strings and leaves full
 * syntax validation to org.json; it only bounds structures that can amplify memory or recursion.
 */
internal object BackupJsonInputGuard {
    const val MAX_OBJECTS = 50_000
    private const val MAX_DEPTH = 64
    private const val MAX_COMMAS = 750_000
    private const val MAX_PROPERTIES = 500_000

    fun accepts(text: String): Boolean = Scanner().accepts(text)

    private class Scanner {
        private var inString = false
        private var escaped = false
        private var depth = 0
        private var objects = 0
        private var commas = 0
        private var properties = 0

        fun accepts(text: String): Boolean {
            for (character in text) {
                if (!accept(character)) return false
            }
            return !inString && depth == 0
        }

        private fun accept(character: Char): Boolean =
            if (inString) acceptStringCharacter(character) else acceptStructuralCharacter(character)

        private fun acceptStringCharacter(character: Char): Boolean {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
            return true
        }

        private fun acceptStructuralCharacter(character: Char): Boolean = when (character) {
            '"' -> {
                inString = true
                true
            }
            '{' -> openContainer(isObject = true)
            '[' -> openContainer(isObject = false)
            '}', ']' -> closeContainer()
            ',' -> countComma()
            ':' -> countProperty()
            else -> true
        }

        private fun openContainer(isObject: Boolean): Boolean {
            if (isObject) {
                objects++
                if (objects > MAX_OBJECTS) return false
            }
            depth++
            return depth <= MAX_DEPTH
        }

        private fun closeContainer(): Boolean {
            depth--
            return depth >= 0
        }

        private fun countComma(): Boolean {
            commas++
            return commas <= MAX_COMMAS
        }

        private fun countProperty(): Boolean {
            properties++
            return properties <= MAX_PROPERTIES
        }
    }
}
