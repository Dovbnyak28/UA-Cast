package com.uacastplayer.playlist

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

sealed class BoundedReadResult {
    data class Success(val text: String) : BoundedReadResult()
    data object SizeLimitExceeded : BoundedReadResult()
}

sealed class BoundedBytesResult {
    data class Success(val bytes: ByteArray) : BoundedBytesResult()
    data object SizeLimitExceeded : BoundedBytesResult()
}

/**
 * Streams [input] in fixed-size chunks and bails out the moment the total exceeds [maxBytes],
 * instead of buffering an entire oversized response just to reject it afterwards. Used on every
 * network/file path that reads a playlist or EPG document.
 */
object BoundedTextReader {

    private const val CHUNK_SIZE = 8192

    /** For callers that already know (or don't care about) the encoding - e.g. an EPG document,
     * or a playlist source whose charset is decided separately (see
     * [com.uacastplayer.playlist.CharsetDetector] and its callers) via [readBytes] instead. */
    fun readText(input: InputStream, maxBytes: Int, charset: Charset = Charsets.UTF_8): BoundedReadResult =
        when (val bounded = readBytes(input, maxBytes)) {
            is BoundedBytesResult.Success -> BoundedReadResult.Success(String(bounded.bytes, charset))
            BoundedBytesResult.SizeLimitExceeded -> BoundedReadResult.SizeLimitExceeded
        }

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
