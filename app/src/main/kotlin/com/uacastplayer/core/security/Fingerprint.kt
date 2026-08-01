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

    // MessageDigest.getInstance() is a JCA provider lookup, not a cheap constructor - measured at
    // ~75% of the total cost of fingerprinting a URL (3008us -> 750us per 5000 digests once reused).
    // That dominates because the digests here are short: a stream URL is one SHA-256 block, so the
    // hashing itself is a rounding error next to finding the implementation that does it.
    //
    // Per thread rather than shared, because MessageDigest is stateful and this is called from the
    // main thread (FavoriteKey.of via isFavorite/isChannelLocked), Dispatchers.IO (IconRepository's
    // candidate chain) and the proxy's own pool threads. One instance per calling thread costs a
    // few hundred bytes each and never contends.
    private val digester = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    // This runs on the main thread once per list row (FavoriteKey.of, reached from isFavorite/
    // isChannelLocked) and once per icon candidate, so the hex encoding is a real hot path, not
    // incidental formatting. The previous joinToString + "%02x".format(...) spent ~32 String.format
    // calls per digest - each one parsing the format string and allocating a Formatter - which
    // dominated the SHA-256 itself. A direct nibble lookup into a single preallocated CharArray
    // does the same job with no intermediate allocation.
    fun of(value: String): String {
        // digest(input) resets the instance on its way out, so a reused one starts clean; reset()
        // here covers only the case of a caller that threw between an update() and a digest(),
        // which no current path does but a future one could.
        val messageDigest = requireNotNull(digester.get())
        messageDigest.reset()
        val digest = messageDigest.digest(value.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val byte = digest[i].toInt()
            hex[i * 2] = HEX_DIGITS[(byte shr BITS_PER_HEX_DIGIT) and HEX_DIGIT_MASK]
            hex[i * 2 + 1] = HEX_DIGITS[byte and HEX_DIGIT_MASK]
        }
        return String(hex)
    }
}
