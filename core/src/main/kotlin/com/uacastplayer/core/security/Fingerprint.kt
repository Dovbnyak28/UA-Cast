package com.uacastplayer.core.security

import java.security.MessageDigest

/**
 * SHA-256 hex digests used everywhere a persisted file would otherwise need to store a raw URL,
 * stream identifier, or device identifier. Never reverse this to recover the original value from
 * persisted state - it is one-way by design.
 */
object Fingerprint {

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
    // incidental formatting - see [Hex], which is where that encoding and the measurement behind
    // its shape now live, shared with [FileDigest].
    fun of(value: String): String {
        // digest(input) resets the instance on its way out, so a reused one starts clean; reset()
        // here covers only the case of a caller that threw between an update() and a digest(),
        // which no current path does but a future one could.
        val messageDigest = requireNotNull(digester.get())
        messageDigest.reset()
        return Hex.encode(messageDigest.digest(value.toByteArray(Charsets.UTF_8)))
    }
}
