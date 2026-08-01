package com.uacastplayer.log

import java.net.URI

/**
 * Redacts URLs, credential-like query params, and long token-looking sequences out of any log
 * message before it reaches [LogBuffer] or Logcat. Applied unconditionally inside [AppLog] so a
 * single careless `AppLog.d { "loading $url" }` call can never leak a proxy session token or an
 * Xtream username/password into a user-shared diagnostics report - the guarantee lives here, not
 * in caller discipline.
 *
 * The marker appended to a redacted value is a deterministic 6-hex-digit hash of the original
 * text, not the text itself: two occurrences of the same URL or token in one log collapse to the
 * same marker, so a developer reading a report can still tell "this is the same value as 3 lines
 * up" without ever seeing the value.
 */
object LogSanitizer {

    /** Minimum contiguous length for an unlabelled sequence to be treated as a token, not a word. */
    private const val MIN_TOKEN_LENGTH = 24
    private const val MARKER_MASK = 0xFFFFFF
    private const val MARKER_HEX_DIGITS = 6
    private const val HEX_RADIX = 16

    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""")
    private val PARAM_REGEX = Regex("""(?i)(username|password|token|auth)=[^&\s"'<>]*""")
    private val TOKEN_REGEX = Regex("""[A-Za-z0-9_+/=-]{$MIN_TOKEN_LENGTH,}""")

    fun sanitize(message: String): String {
        // Cheapest possible rejection for the overwhelmingly common case (a short, plain-text
        // message): no allocation, just three scans of the original reference.
        if (message.length < MIN_TOKEN_LENGTH && !message.contains("http") && !message.contains('=')) {
            return message
        }

        var result = message
        if (result.contains("http")) {
            result = URL_REGEX.replace(result) { redactUrl(it.value) }
        }
        if (result.contains('=')) {
            result = PARAM_REGEX.replace(result) { "${it.groupValues[1]}=<redacted>" }
        }
        // `result.length >= MIN_TOKEN_LENGTH` alone used to gate this - true for almost any log
        // message (ordinary sentences are longer than 24 chars too), so TOKEN_REGEX ran on nearly
        // every call. hasTokenRun actually checks for a contiguous run of token-class characters at
        // least that long, which is rare outside genuine tokens/keys - the regex only runs when
        // there's real work for it to do.
        if (hasTokenRun(result)) {
            result = TOKEN_REGEX.replace(result) { "<token:${shortMarker(it.value)}>" }
        }
        return result
    }

    /** No allocation - a single char-by-char scan for a run of [MIN_TOKEN_LENGTH]+ characters from
     * [TOKEN_REGEX]'s own character class, so this can never say "no token" when the regex would
     * actually find one. */
    private fun hasTokenRun(s: String): Boolean {
        var run = 0
        for (c in s) {
            run = if (isTokenChar(c)) run + 1 else 0
            if (run >= MIN_TOKEN_LENGTH) return true
        }
        return false
    }

    private fun isTokenChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '+' || c == '/' || c == '=' || c == '-'

    /**
     * Keeps only scheme, host and port - enough to tell which origin a log line is about - and
     * replaces everything else with a marker.
     *
     * [URI.getHost], never [URI.getAuthority]: the authority *includes userinfo*, so
     * `http://user:secret@host/path` redacted through it came out as
     * `http://user:secret@host/…`, handing the account's credentials to the very diagnostics report
     * this object exists to keep them out of. The path was being stripped correctly the whole time;
     * only the credentials-before-the-@ form leaked.
     *
     * A host this fails to parse (`URI` is strict - an underscore in a hostname is enough) is
     * redacted whole rather than falling back to the authority. That loses a little context in a
     * report, which is the right way round for a component whose failure mode is a leak.
     */
    private fun redactUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host ?: return "<url:${shortMarker(url)}>"
        val scheme = uri.scheme ?: "http"
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "$scheme://$host$port/…#${shortMarker(url)}"
    }

    /** [String.hashCode] is fixed by the Java language spec, so this is stable across runs/JVMs. */
    private fun shortMarker(input: String): String {
        val hash = input.hashCode() and MARKER_MASK
        return hash.toString(HEX_RADIX).padStart(MARKER_HEX_DIGITS, '0')
    }
}
