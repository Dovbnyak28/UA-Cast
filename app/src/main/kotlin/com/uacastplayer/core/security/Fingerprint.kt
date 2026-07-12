package com.uacastplayer.core.security

import java.security.MessageDigest

/**
 * SHA-256 hex digests used everywhere a persisted file would otherwise need to store a raw URL,
 * stream identifier, or device identifier. Never reverse this to recover the original value from
 * persisted state - it is one-way by design.
 */
object Fingerprint {

    fun of(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
