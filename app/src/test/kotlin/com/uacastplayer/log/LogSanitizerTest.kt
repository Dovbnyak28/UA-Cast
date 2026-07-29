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

    @Test
    fun `a run of exactly 23 token characters is left alone but 24 is redacted`() {
        val justUnder = "prefix ${"a".repeat(23)} suffix"
        val atThreshold = "prefix ${"a".repeat(24)} suffix"

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
}
