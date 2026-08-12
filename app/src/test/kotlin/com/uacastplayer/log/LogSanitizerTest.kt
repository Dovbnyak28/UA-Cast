package com.uacastplayer.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `plain message without sensitive content is returned unchanged with no allocation`() {
        val message = "Loading playlist"

        val result = LogSanitizer.sanitize(message)

        assertSame(message, result)
    }

    @Test
    fun `proxy url with uuid path and token query is stripped to scheme and host`() {
        val token = "6f9c3b0a1d2e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a"
        val message = "Proxy fallback loading receiver from http://192.168.0.177:40941/hls/abc-123-uuid?token=$token"

        val result = LogSanitizer.sanitize(message)

        assertFalse(result.contains(token))
        assertFalse(result.contains("abc-123-uuid"))
        assertTrue(result.contains("http://192.168.0.177:40941/…#"))
    }

    @Test
    fun `xtream url with credentials in query is fully stripped of the raw values`() {
        val message = "loading http://provider.example/get.php?username=realuser&password=hunter2&type=m3u"

        val result = LogSanitizer.sanitize(message)

        assertFalse(result.contains("realuser"))
        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("http://provider.example/…#"))
    }

    @Test
    fun `bare token outside a url is replaced with a deterministic marker`() {
        val token = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"
        val message = "session id is $token here"

        val result = LogSanitizer.sanitize(message)

        assertFalse(result.contains(token))
        assertTrue(result.contains("<token:"))
    }

    @Test
    fun `credential params outside a url are redacted without a url present`() {
        val message = "raw query username=realuser&password=hunter2&auth=secret123"

        val result = LogSanitizer.sanitize(message)

        assertFalse(result.contains("realuser"))
        assertFalse(result.contains("hunter2"))
        assertFalse(result.contains("secret123"))
        assertTrue(result.contains("username=<redacted>"))
        assertTrue(result.contains("password=<redacted>"))
        assertTrue(result.contains("auth=<redacted>"))
    }

    @Test
    fun `same url sanitized twice produces the same marker`() {
        val message = "loading http://192.168.0.177:40941/hls/abc-123-uuid/some-long-path-segment-here"

        val first = LogSanitizer.sanitize(message)
        val second = LogSanitizer.sanitize(message)

        assertEquals(first, second)
    }

    @Test
    fun `different urls produce different markers`() {
        val first = LogSanitizer.sanitize("loading http://host-one.example/path/one")
        val second = LogSanitizer.sanitize("loading http://host-two.example/path/two")

        assertFalse(first == second)
    }

    @Test
    fun `short message with an equals sign but no credential key is left alone`() {
        val message = "ratio=16:9"

        val result = LogSanitizer.sanitize(message)

        assertEquals("ratio=16:9", result)
    }

    // Regression guard for the Block 3 fix: `result.length >= MIN_TOKEN_LENGTH` used to gate
    // TOKEN_REGEX, which is true for almost any real log line (ordinary sentences are longer than
    // 24 chars too) - so the regex ran on nearly every call. It should only run when there's an
    // actual contiguous token-length run of candidate characters.
    @Test
    fun `a long ordinary sentence with no token-length run is returned unchanged with no allocation`() {
        val message = "This is a perfectly ordinary diagnostic message with many separate short words in it"

        val result = LogSanitizer.sanitize(message)

        assertSame(message, result)
    }

    /**
     * The length boundary, measured on something that is actually a token.
     *
     * This used to be `"a".repeat(24)`, which is not a token - it is twenty-four letters, and
     * asserting that it gets redacted was asserting the defect that ate `BehindLiveWindowException`
     * out of a real user's report. The run now carries a digit, which is what separates a secret
     * from a word; the 23-vs-24 boundary it was written to pin is unchanged and still tested.
     */
    @Test
    fun `a run of exactly 23 token characters is left alone but 24 is redacted`() {
        val justUnder = "prefix 1${"a".repeat(22)} suffix"
        val atThreshold = "prefix 1${"a".repeat(23)} suffix"

        assertSame(justUnder, LogSanitizer.sanitize(justUnder))
        assertTrue(LogSanitizer.sanitize(atThreshold).contains("<token:"))
    }

    @Test
    fun `long log line with mixed content does not throw and drops the sensitive substrings`() {
        val token = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1"
        val message = "cast status: watchdog fired mode=PROXY url=http://10.0.0.5:8080/x/y?password=secret&t=$token"

        val result = LogSanitizer.sanitize(message)

        assertFalse(result.contains("secret"))
        assertFalse(result.contains(token))
    }

    // Credentials embedded in the url's userinfo (`scheme://user:pass@host/`) used to survive
    // redaction verbatim, because URI.getAuthority includes userinfo where getHost does not. This is
    // the one leak class this object exists to prevent, so it gets its own group of tests.

    @Test
    fun `strips credentials embedded before the at-sign`() {
        val result = LogSanitizer.sanitize("loading http://joe:hunter2@iptv.example.com/live/1.ts")

        assertFalse("leaked user, got: $result", result.contains("joe"))
        assertFalse("leaked password, got: $result", result.contains("hunter2"))
        assertFalse("leaked userinfo separator, got: $result", result.contains("@"))
    }

    @Test
    fun `strips a userinfo with no password`() {
        val result = LogSanitizer.sanitize("loading http://joe@iptv.example.com/live/1.ts")

        assertFalse("leaked user, got: $result", result.contains("joe"))
        assertFalse("leaked userinfo separator, got: $result", result.contains("@"))
    }

    @Test
    fun `keeps the host so a report still says which origin a line is about`() {
        val result = LogSanitizer.sanitize("loading http://joe:hunter2@iptv.example.com/live/1.ts")

        assertTrue("host was dropped, got: $result", result.contains("iptv.example.com"))
    }

    @Test
    fun `keeps a non-default port alongside the host`() {
        val result = LogSanitizer.sanitize("loading http://iptv.example.com:8080/live/1.ts")

        assertTrue("port was dropped, got: $result", result.contains("iptv.example.com:8080"))
    }

    @Test
    fun `still strips the path, which is where Xtream puts its credentials`() {
        val result = LogSanitizer.sanitize("loading http://iptv.example.com/live/joe/hunter2/1.ts")

        assertFalse("leaked user, got: $result", result.contains("joe"))
        assertFalse("leaked password, got: $result", result.contains("hunter2"))
    }

    /** An unparseable host falls back to redacting the whole url rather than to the authority -
     * losing context in a report is the right trade for a component whose failure mode is a leak. */
    @Test
    fun `a url whose host cannot be parsed is redacted whole`() {
        val result = LogSanitizer.sanitize("loading http://joe:hunter2@host_with_underscore/x")

        assertFalse("leaked password, got: $result", result.contains("hunter2"))
        assertTrue("expected a whole-url marker, got: $result", result.contains("<url:"))
    }

    /**
     * Reproduced from a real diagnostics report, which carried the line
     * `Recovering from <token:8f1bf9>` - and `8f1bf9` is this app's own marker for the string
     * `BehindLiveWindowException`, a hard-coded literal in `PlayerViewModel` with nothing secret
     * in it. Length alone made it a token: 25 characters of letters.
     */
    @Test
    fun `a long class name is not a token`() {
        val result = LogSanitizer.sanitize("Recovering from BehindLiveWindowException (attempt 1 in the last 60s)")

        assertTrue("the name was eaten, got: $result", result.contains("BehindLiveWindowException"))
    }

    /**
     * The same defect, and the one that costs the most: this app logs `e.javaClass.simpleName`
     * rather than `e.message` all over, specifically so the result is safe to put in a report. The
     * ones long enough to matter were the ones being redacted.
     */
    @Test
    fun `the exception names this app deliberately logs survive`() {
        val names = listOf(
            "IllegalArgumentException",
            "TransactionTooLargeException",
            "ConcurrentModificationException",
            "UnsupportedOperationException",
        )

        for (name in names) {
            val result = LogSanitizer.sanitize("Favorites write failed: $name")
            assertTrue("$name was eaten, got: $result", result.contains(name))
        }
    }

    /** A Logcat tag is a word with a `/` in it, not a secret - `08-12 16:58:19.810 <token:158b95>`
     * is what a full log looked like where the tag ran past 23 characters. */
    @Test
    fun `a long logcat tag survives`() {
        val result = LogSanitizer.sanitize("08-12 16:58:19.810 I/NavigationEventDispatcher( 1692): dispatched")

        assertTrue("the tag was eaten, got: $result", result.contains("I/NavigationEventDispatcher"))
    }

    /** The other half of the same rule: a digit anywhere in the run still makes it a token, which
     * is every format this app could actually leak - hex ids, base64, signed payloads. */
    @Test
    fun `a token with digits in it is still redacted`() {
        val secrets = listOf(
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
            "eyJhbGciOiJIUzI1NiJ9aGVsbG8gd29ybGQ",
            "session_9f3a2b81c7d64e05af12b3c4d5e6f708",
        )

        for (secret in secrets) {
            val result = LogSanitizer.sanitize("proxy handoff for $secret")
            assertFalse("leaked $secret, got: $result", result.contains(secret))
            assertTrue("expected a token marker, got: $result", result.contains("<token:"))
        }
    }
}
