package com.uacastplayer.core.security

import java.security.MessageDigest

/**
 * SHA-256 hex digests used everywhere a persisted file would otherwise need to store a raw URL,
 * stream identifier, or device identifier. Never reverse this to recover the original value from
 * persisted state - it is one-way by design.
 */
object Fingerprint {

    // Plain ASCII [0-9a-f], written out rather than derived, so the hex encoding below can never
    // depend on the device's default locale the way String.format would (these digests are used as
    // filenames and cache keys - a locale-dependent digit would silently orphan every cached entry).
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val BITS_PER_HEX_DIGIT = 4
    private const val HEX_DIGIT_MASK = 0xF

    // This runs on the main thread once per list row (FavoriteKey.of, reached from isFavorite/
    // isChannelLocked) and once per icon candidate, so the hex encoding is a real hot path, not
    // incidental formatting. The previous joinToString + "%02x".format(...) spent ~32 String.format
    // calls per digest - each one parsing the format string and allocating a Formatter - which
    // dominated the SHA-256 itself. A direct nibble lookup into a single preallocated CharArray
    // does the same job with no intermediate allocation.
    fun of(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val byte = digest[i].toInt()
            hex[i * 2] = HEX_DIGITS[(byte shr BITS_PER_HEX_DIGIT) and HEX_DIGIT_MASK]
            hex[i * 2 + 1] = HEX_DIGITS[byte and HEX_DIGIT_MASK]
        }
        return String(hex)
    }
}
