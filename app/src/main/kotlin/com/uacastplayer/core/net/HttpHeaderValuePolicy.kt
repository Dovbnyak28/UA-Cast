package com.uacastplayer.core.net

/** Bounds and validates per-channel HTTP header values before they reach OkHttp/Media3. */
internal object HttpHeaderValuePolicy {
    const val MAX_VALUE_LENGTH = 8 * 1024

    fun sanitize(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf { candidate ->
            candidate.isNotEmpty() && candidate.length <= MAX_VALUE_LENGTH &&
                candidate.all { character -> character == '\t' || character in '\u0020'..'\u007e' }
        }
    }

    fun userAgentOrDefault(value: String?): String =
        sanitize(value) ?: HttpDefaults.BROWSER_USER_AGENT
}
