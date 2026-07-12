package com.uacastplayer.playlist

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

sealed class BoundedReadResult {
    data class Success(val text: String) : BoundedReadResult()
    data object SizeLimitExceeded : BoundedReadResult()
}

/**
 * Streams [input] in fixed-size chunks and bails out the moment the total exceeds [maxBytes],
 * instead of buffering an entire oversized response just to reject it afterwards. Used on every
 * network/file path that reads a playlist or EPG document.
 */
object BoundedTextReader {

    private const val CHUNK_SIZE = 8192

    fun readText(input: InputStream, maxBytes: Int, charset: Charset = Charsets.UTF_8): BoundedReadResult {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0

        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) return BoundedReadResult.SizeLimitExceeded
            buffer.write(chunk, 0, read)
        }

        return BoundedReadResult.Success(buffer.toString(charset.name()))
    }
}
