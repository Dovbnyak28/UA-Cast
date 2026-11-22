package app.zorik.player.data.cast

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Distinguishes an origin read/connect failure from a renderer that abandoned its socket. */
internal class OriginStreamException(cause: IOException) : IOException(cause)

internal class ReceiverStreamException(cause: IOException) : IOException(cause)

/**
 * Stateless byte-copy steps used by [HlsFlattenedStream] to move one TS segment from the origin to
 * the receiver. Origin failures surface as [OriginStreamException], receiver failures as
 * [ReceiverStreamException], so the caller can tell which side of the proxy broke.
 */
internal object HlsStreamCopy {
    /** Reads until more than [tsPacketSizeBytes] bytes are buffered; null if the origin ends first. */
    fun readInitialBytes(input: InputStream, chunk: ByteArray, tsPacketSizeBytes: Int): Int? {
        var buffered = 0
        while (buffered <= tsPacketSizeBytes) {
            val read = try {
                input.read(chunk, buffered, chunk.size - buffered)
            } catch (error: IOException) {
                throw OriginStreamException(error)
            }
            if (read == -1) return null
            if (read > 0) buffered += read
        }
        return buffered
    }

    fun writeInitialBytes(
        output: OutputStream,
        chunk: ByteArray,
        buffered: Int,
        onFirstBytes: () -> Unit,
    ) {
        try {
            onFirstBytes()
            output.write(chunk, 0, buffered)
        } catch (error: IOException) {
            throw ReceiverStreamException(error)
        }
    }

    fun copyRemaining(
        input: InputStream,
        output: OutputStream,
        chunk: ByteArray,
    ): Long {
        var copied = 0L
        while (true) {
            val read = try {
                input.read(chunk)
            } catch (error: IOException) {
                throw OriginStreamException(error)
            }
            if (read == -1) return copied
            if (read > 0) {
                try {
                    output.write(chunk, 0, read)
                } catch (error: IOException) {
                    throw ReceiverStreamException(error)
                }
                copied += read
            }
        }
    }

    fun flushReceiver(output: OutputStream) {
        try {
            output.flush()
        } catch (error: IOException) {
            throw ReceiverStreamException(error)
        }
    }
}
