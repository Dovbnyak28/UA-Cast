package com.uacastplayer.core.security

import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 hex digests used everywhere a persisted file would otherwise need to store a raw URL,
 * stream identifier, or device identifier. Never reverse this to recover the original value from
 * persisted state - it is one-way by design.
 */
object Fingerprint {

    fun of(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        // Locale.ROOT: these hex digests are used as filenames/cache keys elsewhere, so they must
        // always come out as plain ASCII [0-9a-f] regardless of the device's default locale.
        return digest.joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
    }
}
