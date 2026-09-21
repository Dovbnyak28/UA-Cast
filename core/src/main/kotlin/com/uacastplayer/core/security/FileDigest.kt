package com.uacastplayer.core.security

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The SHA-256 of a file's bytes, as lowercase hex.
 *
 * Separate from [Fingerprint], which hashes short strings into cache keys and holds a reused
 * [MessageDigest] per thread because the lookup dominates at that size. Here the file is tens of
 * megabytes, so the provider lookup is a rounding error and a fresh instance per call is simpler
 * and holds no state between downloads.
 *
 * Streamed in chunks rather than read whole: this exists to check a downloaded APK, and reading a
 * 40MB update into a ByteArray to hash it would undo the point of having streamed it to disk.
 */
object FileDigest {

    private const val CHUNK_SIZE = 8192

    /** Null when the file cannot be read - a caller checking a download against a published hash
     * must treat that as "not verified", which is the same answer as a mismatch. */
    fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> input.updateAll(digest) }
        Hex.encode(digest.digest())
    } catch (_: IOException) {
        null
    }

    private fun InputStream.updateAll(digest: MessageDigest) {
        val chunk = ByteArray(CHUNK_SIZE)
        while (true) {
            val read = read(chunk)
            if (read == -1) return
            digest.update(chunk, 0, read)
        }
    }
}
