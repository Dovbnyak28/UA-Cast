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

    /**
     * The MessageDigest instance is reused per thread rather than created per call (see
     * [Fingerprint]), so a leftover-state bug would not show up as a crash - it would show up as a
     * digest that is correct the first time and wrong afterwards, silently orphaning cached icons
     * from the second call onward. Interleaves distinct inputs so any carried-over state would have
     * to survive an intervening digest of something else.
     */
    @Test
    fun `repeated interleaved calls on one thread all match a fresh digest`() {
        val inputs = listOf("hello", "http://example.com/a.ts", "", "Дітячі канали", "hello")
        repeat(3) {
            for (input in inputs) {
                val expected = referenceDigest(input)
                assertEquals("digest mismatch for repeated input '$input'", expected, Fingerprint.of(input))
            }
        }
    }

    /** The instance is per thread, so nothing is shared - but that is the claim the comment makes,
     * and this is what would fail loudly if it were ever made a shared field instead. */
    @Test
    fun `concurrent callers each get the correct digest for their own input`() {
        val inputs = (0 until 8).map { "http://example.com/stream$it.ts" }
        val expected = inputs.associateWith { referenceDigest(it) }
        val actual = java.util.concurrent.ConcurrentHashMap<String, String>()

        val threads = inputs.map { input ->
            Thread {
                repeat(200) { actual[input] = Fingerprint.of(input) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(expected, actual.toMap())
    }

    private fun referenceDigest(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}
