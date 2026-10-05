package com.uacastplayer.core.security

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val SALT_BYTES = 16
private const val PBKDF2_ITERATIONS = 120_000
private const val PBKDF2_KEY_BITS = 256
private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

/**
 * PIN hashing for the parental-control lock (see `parentalcontrol/ParentalControlPinPolicy`) -
 * deliberately separate from [Fingerprint], which is unsalted single-round SHA-256 documented as
 * scoped to non-secret identifiers (URLs, device ids). A 4-digit PIN has only 10,000 possible
 * values regardless of hashing strength, so this is UX-level protection against a casual look at
 * someone else's phone, not a defense against an attacker with the persisted salt+hash and time to
 * brute-force - but salting still stops a rainbow-table lookup, and the iteration count still
 * costs an offline attacker something.
 */
object PinHasher {

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        val password = pin.toCharArray()
        val spec = PBEKeySpec(password, saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        password.fill('\u0000')
        return try {
            val derived = try {
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            } catch (_: NoSuchAlgorithmException) {
                // Android 24/25 has HmacSHA256 but not its PBKDF2 SecretKeyFactory (API 26+).
                // Keep the SAME salt encoding, work factor and output so restored hashes work.
                Pbkdf2HmacSha256.derive(pin, saltBytes, PBKDF2_ITERATIONS)
            }
            try {
                derived.toHex()
            } finally {
                derived.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison via [MessageDigest.isEqual] - no reason to leak timing information
     * about how many leading hash bytes matched for a value this cheap to just recompute anyway. */
    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}
