package com.uacastplayer.data.remote

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Both authentication and every command use AEAD; the PIN and commands are never sent in cleartext. */
internal object RemoteCipher {
    private const val KEY_BITS = 256
    private const val ITERATIONS = 160_000
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_FRAME_BYTES = 256
    const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun salt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun key(code: String, salt: ByteArray): SecretKey {
        require(code.length == RemotePairingCode.DIGITS && code.all { it in '0'..'9' })
        require(salt.size == SALT_BYTES)
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            // HMAC-SHA1 as PBKDF2's PRF is supported on API 24 too. Its hash-collision weakness
            // does not apply to password stretching. The same PRF must work on both devices.
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    fun write(output: DataOutputStream, key: SecretKey, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_BYTES - NONCE_BYTES - TAG_BITS / Byte.SIZE_BITS)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(payload)
        output.writeInt(nonce.size + encrypted.size)
        output.write(nonce)
        output.write(encrypted)
        output.flush()
    }

    fun read(input: DataInputStream, key: SecretKey): ByteArray {
        val length = input.readInt()
        if (length !in (NONCE_BYTES + TAG_BITS / Byte.SIZE_BITS)..MAX_FRAME_BYTES) {
            throw IOException("Invalid remote frame")
        }
        val nonce = ByteArray(NONCE_BYTES).also(input::readFully)
        val encrypted = ByteArray(length - NONCE_BYTES).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(encrypted)
    }
}
