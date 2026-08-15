package com.uacastplayer.core.security

/**
 * Bytes to lowercase hex, without going through `String.format`.
 *
 * Lifted out of [Fingerprint] when [FileDigest] needed the same encoding, so there is one of these
 * rather than two that could drift. Both reasons it looks like this came from there and both still
 * hold:
 *
 * The digit table is written out rather than derived, so the encoding can never depend on the
 * device's default locale the way `String.format` would - these digests are used as filenames and
 * cache keys, and a locale-dependent digit would silently orphan every cached entry.
 *
 * The nibble lookup into one preallocated [CharArray] is there because [Fingerprint.of] runs on the
 * main thread once per list row. `joinToString` with `"%02x".format(...)` spent ~32 `String.format`
 * calls per digest - each parsing the format string and allocating a `Formatter` - which dominated
 * the SHA-256 itself.
 */
internal object Hex {

    private const val DIGITS = "0123456789abcdef"
    private const val BITS_PER_DIGIT = 4
    private const val DIGIT_MASK = 0xF

    fun encode(bytes: ByteArray): String {
        val hex = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val byte = bytes[i].toInt()
            hex[i * 2] = DIGITS[(byte shr BITS_PER_DIGIT) and DIGIT_MASK]
            hex[i * 2 + 1] = DIGITS[byte and DIGIT_MASK]
        }
        return String(hex)
    }
}
