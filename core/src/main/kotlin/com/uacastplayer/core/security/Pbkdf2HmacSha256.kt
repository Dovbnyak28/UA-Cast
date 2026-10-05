package com.uacastplayer.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Fixed 256-bit PBKDF2-HMAC-SHA256 fallback for providers without the PBKDF2 factory.
 * RFC 8018 section 5.2: one output block, U1 = PRF(password, salt || INT_32_BE(1)),
 * then XOR every subsequent U value. The platform still supplies the HMAC primitive.
 * https://www.rfc-editor.org/rfc/rfc8018#section-5.2
 */
internal object Pbkdf2HmacSha256 {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0) { "PBKDF2 iterations must be positive" }
        // JCA rejects an empty SecretKeySpec; a single zero byte has the same HMAC key
        // block after zero-padding. Keep empty input compatible with the native factory.
        val key = if (password.isEmpty()) byteArrayOf(0) else password.toByteArray(Charsets.UTF_8)
        try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            val u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
            val result = u.copyOf()
            try {
                repeat(iterations - 1) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (index in result.indices) {
                        result[index] = (result[index].toInt() xor u[index].toInt()).toByte()
                    }
                }
                return result
            } finally {
                u.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }
}
