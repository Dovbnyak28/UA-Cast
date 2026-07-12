package com.uacastplayer.epg

import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed class BoundedBytesResult {
    data class Success(val bytes: ByteArray) : BoundedBytesResult() {
        override fun equals(other: Any?): Boolean =
            other is Success && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data object SizeLimitExceeded : BoundedBytesResult()
}

/** Same streaming-with-early-bailout shape as [com.uacastplayer.playlist.BoundedTextReader], for binary content (the EPG gzip blob is stored as-is and must not go through text decoding). */
object BoundedByteReader {

    private const val CHUNK_SIZE = 8192

    fun readBytes(input: InputStream, maxBytes: Int): BoundedBytesResult {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0

        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) return BoundedBytesResult.SizeLimitExceeded
            buffer.write(chunk, 0, read)
        }

        return BoundedBytesResult.Success(buffer.toByteArray())
    }
}
