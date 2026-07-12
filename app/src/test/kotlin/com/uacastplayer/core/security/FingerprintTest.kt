package com.uacastplayer.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {

    @Test
    fun `known input produces the expected SHA-256 hex digest`() {
        // sha256("hello") is a well-known test vector.
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Fingerprint.of("hello"),
        )
    }

    @Test
    fun `is deterministic for the same input`() {
        assertEquals(Fingerprint.of("http://example.com/playlist.m3u"), Fingerprint.of("http://example.com/playlist.m3u"))
    }

    @Test
    fun `different inputs produce different digests`() {
        assertNotEquals(Fingerprint.of("a"), Fingerprint.of("b"))
    }

    @Test
    fun `output is 64 lowercase hex characters`() {
        val digest = Fingerprint.of("anything")
        assertEquals(64, digest.length)
        assertEquals(digest, digest.lowercase())
        assertEquals(true, digest.all { it in "0123456789abcdef" })
    }
}
