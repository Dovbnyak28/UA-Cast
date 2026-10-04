package com.uacastplayer.core.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Portable password encryption, independent of Android Keystore/device identity.
 * PBKDF2-HMAC-SHA256 follows RFC 8018 (one 256-bit block). Using JCA HMAC also supports
 * API 24/25, where the SHA256 SecretKeyFactory is unavailable, and permits cancellation. */
object BackupCipher {
    const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 128
    const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val VERSION = 1
    private const val KEY_BYTES = 32
    private const val CHECK_INTERVAL = 4096
    private val magic = "UACASTBK".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()
    private val headerBytes = magic.size + 1 + Int.SIZE_BYTES + SALT_BYTES + NONCE_BYTES
    val maxFileBytes: Int = MAX_PLAINTEXT_BYTES + headerBytes + TAG_BYTES

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    fun acceptsPassword(password: CharArray): Boolean =
        password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH && password.any { !it.isWhitespace() }

    fun encrypt(plain: ByteArray, password: CharArray, checkCancellation: () -> Unit = {}): ByteArray {
        require(plain.size <= MAX_PLAINTEXT_BYTES && acceptsPassword(password))
        checkCancellation()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(headerBytes).put(magic).put(VERSION.toByte()).putInt(ITERATIONS)
            .put(salt).put(nonce).array()
        val key = deriveKey(password, salt, ITERATIONS, checkCancellation)
        return try {
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce)
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plain)
            checkCancellation()
            header + encrypted
        } finally { key.fill(0) }
    }

    /** Header/size/work-factor checks precede the KDF. Authentication precedes plaintext use.
     * Wrong password and tampering both fail authentication; no partial plaintext is exposed. */
    fun decrypt(bytes: ByteArray, password: CharArray, checkCancellation: () -> Unit = {}): ByteArray {
        require(isEncrypted(bytes) && bytes.size in (headerBytes + TAG_BYTES)..maxFileBytes)
        require(acceptsPassword(password))
        val header = ByteBuffer.wrap(bytes, 0, headerBytes)
        header.position(magic.size)
        require(header.get().toInt() == VERSION && header.int == ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also(header::get)
        val nonce = ByteArray(NONCE_BYTES).also(header::get)
        val key = deriveKey(password, salt, ITERATIONS, checkCancellation)
        return try {
            val cipher = cipher(Cipher.DECRYPT_MODE, key, nonce)
            cipher.updateAAD(bytes, 0, headerBytes)
            val plain = cipher.doFinal(bytes, headerBytes, bytes.size - headerBytes)
            checkCancellation()
            plain
        } finally { key.fill(0) }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, nonce))
        }

    internal fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        rounds: Int,
        checkCancellation: () -> Unit = {},
    ): ByteArray {
        require(rounds > 0 && password.isNotEmpty())
        checkCancellation()
        val passwordBytes = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        val encoded = ByteArray(passwordBytes.remaining()).also(passwordBytes::get)
        val mac = Mac.getInstance("HmacSHA256")
        try { mac.init(SecretKeySpec(encoded, "HmacSHA256")) } finally {
            encoded.fill(0)
            if (passwordBytes.hasArray()) passwordBytes.array().fill(0)
        }
        var u = ByteArray(KEY_BYTES)
        var next = ByteArray(KEY_BYTES)
        val result = ByteArray(KEY_BYTES)
        var completed = false
        try {
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1))
            mac.doFinal(u, 0)
            u.copyInto(result)
            for (round in 1 until rounds) {
                if (round % CHECK_INTERVAL == 0) checkCancellation()
                mac.update(u)
                mac.doFinal(next, 0)
                for (i in result.indices) result[i] = (result[i].toInt() xor next[i].toInt()).toByte()
                val previous = u
                u = next
                next = previous
            }
            checkCancellation()
            completed = true
            return result
        } finally {
            if (!completed) result.fill(0)
            u.fill(0)
            next.fill(0)
        }
    }
}
