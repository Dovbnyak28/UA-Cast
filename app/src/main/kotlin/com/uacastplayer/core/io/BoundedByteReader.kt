package com.uacastplayer.core.io

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed interface BoundedBytesResult {
    data class Success(val bytes: ByteArray) : BoundedBytesResult {
        override fun equals(other: Any?): Boolean =
            other is Success && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data object SizeLimitExceeded : BoundedBytesResult
}

sealed interface BoundedFileCopyResult {
    data class Success(val bytesWritten: Long) : BoundedFileCopyResult
    data object SizeLimitExceeded : BoundedFileCopyResult
}

/** Same streaming-with-early-bailout shape as
 * [com.uacastplayer.playlist.BoundedTextReader], for binary content such as EPG gzip blobs. */
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

    /**
     * Reads up to [maxBytes] from [input] and stops there, returning whatever was captured -
     * unlike [readBytes], a source that keeps sending data past the cap is not treated as an
     * error. Meant for callers that only need a bounded prefix regardless of how much more an
     * upstream server decides to send (e.g. codec-sniffing a media segment where a misbehaving
     * server ignored a Range request).
     */
    fun readAtMostBytes(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(chunk.size, maxBytes - total)
            val read = input.read(chunk, 0, toRead)
            if (read == -1) break
            total += read
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Streams [input] into [destination] chunk-by-chunk, never holding more than [CHUNK_SIZE]
     * bytes in memory at once - unlike [readBytes], this is meant for large downloads (e.g.
     * multi-megabyte EPG feeds) where buffering the whole body as a ByteArray would be wasteful.
     * [destination] is left with whatever was written so far if the limit is exceeded; callers
     * are expected to delete it in that case.
     */
    fun copyToFile(input: InputStream, destination: File, maxBytes: Int): BoundedFileCopyResult {
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0L
        FileOutputStream(destination).use { out ->
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) return BoundedFileCopyResult.SizeLimitExceeded
                out.write(chunk, 0, read)
            }
        }
        return BoundedFileCopyResult.Success(total)
    }
}
