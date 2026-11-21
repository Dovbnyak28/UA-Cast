package com.uacastplayer.playlist

import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import java.io.InputStream
import java.nio.charset.Charset

sealed interface BoundedReadResult {
    data class Success(val text: String) : BoundedReadResult
    data object SizeLimitExceeded : BoundedReadResult
}

/**
 * Streams [input] in fixed-size chunks and bails out the moment the total exceeds [maxBytes],
 * instead of buffering an entire oversized response just to reject it afterwards. Used on every
 * network/file path that reads a playlist or EPG document.
 */
object BoundedTextReader {
    /** For callers that already know (or don't care about) the encoding - e.g. an EPG document,
     * or a playlist source whose charset is decided separately (see
     * [com.uacastplayer.playlist.CharsetDetector] and its callers) via
     * [BoundedByteReader.readBytes] instead. */
    fun readText(input: InputStream, maxBytes: Int, charset: Charset = Charsets.UTF_8): BoundedReadResult =
        when (val bounded = BoundedByteReader.readBytes(input, maxBytes)) {
            is BoundedBytesResult.Success -> BoundedReadResult.Success(String(bounded.bytes, charset))
            BoundedBytesResult.SizeLimitExceeded -> BoundedReadResult.SizeLimitExceeded
        }
}
