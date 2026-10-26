package com.uacastplayer.log

import com.uacastplayer.core.concurrent.runCatchingNonFatal
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

    /**
     * Any scheme, not just `http`/`https`.
     *
     * [redactUrl] below has always been scheme-agnostic - it asks [URI] for the host and keeps
     * nothing else - so `http` here was the one place the guarantee narrowed to a protocol. An M3U
     * entry's `streamUrl` is whatever the provider wrote, and `rtmp://`, `rtsp://` and `mms://` all
     * appear in real playlists, with credentials in exactly the two shapes this exists to catch:
     * `rtmp://user:pass@host/live` and `rtmp://host/user/pass/1234.ts`. Measured before the change:
     * both passed through this object completely unaltered, password included.
     *
     * Nothing logs a raw stream url today. That is the point - this object's own contract is that a
     * single careless call "can never" leak one, and a guarantee that rests on no caller ever
     * writing that line is caller discipline by another name.
     */
    private val URL_REGEX = Regex("""[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>]+""")

    /** What [URL_REGEX] needs to be present at all - checked before the regex runs, and before the
     * cheap rejection below decides a message is too short to hold anything. */
    private const val SCHEME_MARKER = "://"
    private val PARAM_REGEX = Regex("""(?i)(username|password|token|auth)=[^&\s"'<>]*""")
    private val TOKEN_REGEX = Regex("""[A-Za-z0-9_+/=-]{$MIN_TOKEN_LENGTH,}""")

    /**
     * A run this long with no digit anywhere in it is a word, not a secret.
     *
     * Length alone was the whole test, and it ate this app's own diagnostics. Java class names are
     * long: `BehindLiveWindowException` is 25 characters, so the line
     * `"Recovering from BehindLiveWindowException"` - a hard-coded literal in
     * [com.uacastplayer.player.PlayerViewModel] with nothing secret in it - reached a real user's
     * report as `Recovering from <token:8f1bf9>`. So did every `e.javaClass.simpleName` this app
     * logs deliberately in place of an exception message: `IllegalArgumentException` (24),
     * `TransactionTooLargeException` (28), `ConcurrentModificationException` (31). The messages
     * written specifically to be safe to share were the ones being destroyed, and long Logcat tags
     * went with them.
     *
     * The requirement is a digit rather than "letters only" because a run's non-letter is usually
     * the `/` in a Logcat tag or the `-` in an identifier, neither of which makes it a secret.
     *
     * What this gives up, stated plainly: a purely alphabetic secret of 24+ characters would now
     * survive this net. It is a net, not the defence - a token in a URL is caught by [URL_REGEX], a
     * credential in a query by [PARAM_REGEX], and the formats this app can actually leak (hex
     * session ids, base64 tokens) contain digits with probability that rounds to certainty: a
     * random 24-character base64 string has about a 1% chance of having none.
     */
    private fun looksLikeAWord(run: String): Boolean = run.none { it in '0'..'9' }

    fun sanitize(message: String): String {
        // Cheapest possible rejection for the overwhelmingly common case (a short, plain-text
        // message): no allocation, just three scans of the original reference.
        if (message.length < MIN_TOKEN_LENGTH && !message.contains(SCHEME_MARKER) && !message.contains('=')) {
            return message
        }

        var result = message
        if (result.contains(SCHEME_MARKER)) {
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
            result = TOKEN_REGEX.replace(result) { match ->
                if (looksLikeAWord(match.value)) match.value else "<token:${shortMarker(match.value)}>"
            }
        }
        return result
    }

    /** No allocation - a single char-by-char scan for a run of [MIN_TOKEN_LENGTH]+ characters from
     * [TOKEN_REGEX]'s own character class that also contains a digit, so this can never say "no
     * token" when the replacement above would actually redact one. Runs the digit test here as
     * well as in [looksLikeAWord] so that an ordinary sentence never reaches the regex at all -
     * which was the point of this scan to begin with. */
    private fun hasTokenRun(s: String): Boolean {
        var run = 0
        var digitInRun = false
        for (c in s) {
            if (isTokenChar(c)) {
                run++
                if (c in '0'..'9') digitInRun = true
            } else {
                run = 0
                digitInRun = false
            }
            if (run >= MIN_TOKEN_LENGTH && digitInRun) return true
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
        val uri = runCatchingNonFatal { URI(url) }.getOrNull()
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
