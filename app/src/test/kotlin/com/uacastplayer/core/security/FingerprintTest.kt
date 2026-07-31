package com.uacastplayer.core.security

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `output stays plain ASCII hex under a non-ASCII-digit default locale`() {
        Locale.setDefault(Locale("ar"))
        val digest = Fingerprint.of("http://example.com/playlist.m3u")
        assertEquals(64, digest.length)
        assertEquals(true, digest.all { it in "0123456789abcdef" })
    }

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

    /**
     * These digests are filenames in the on-disk icon cache and keys in the icon-failure store, so
     * the hex encoding is a persisted format, not an implementation detail: any change to it
     * silently orphans every cached icon on every existing install. Pins the current encoding
     * against a from-scratch reference implementation over inputs that exercise both nibbles of
     * high-bit (negative, as a signed Kotlin Byte) digest bytes, which is where a hand-rolled hex
     * conversion would realistically go wrong.
     */
    @Test
    fun `hex encoding matches a reference implementation for every byte value`() {
        val inputs = listOf(
            "",
            "hello",
            "http://example.com/playlist.m3u?username=u&password=p",
            "https://cdn.example.org/logos/1%2B1.png",
            "Дітячі канали",
        )
        for (input in inputs) {
            val expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
            assertEquals("digest mismatch for input '$input'", expected, Fingerprint.of(input))
        }
    }
}
