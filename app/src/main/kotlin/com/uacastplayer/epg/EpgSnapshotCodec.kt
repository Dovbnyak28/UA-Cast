package com.uacastplayer.epg

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [documentStream] is [input] itself, left positioned right after the header so callers can read
 * the payload straight off it (there is nothing else in the file after the payload, so reading
 * until EOF is safe) without this codec ever buffering the document in memory.
 */
data class DecodedEpgSnapshot(
    val header: EpgSnapshotHeader,
    val documentStream: InputStream,
)

/**
 * Versioned binary (de)serializer for an [EpgSnapshotHeader] plus its document payload. The
 * payload is streamed directly between [documentStream]/[output] rather than buffered as a
 * ByteArray - EPG documents can run tens of megabytes.
 */
object EpgSnapshotCodec {

    private const val FORMAT_VERSION = 1
    private const val COPY_BUFFER_SIZE = 8192

    fun encode(header: EpgSnapshotHeader, documentStream: InputStream, documentLength: Long, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(header.sourceFingerprint)
        out.writeLong(header.savedAtEpochMillis)
        out.writeLong(documentLength)
        out.flush()
        documentStream.copyTo(output, COPY_BUFFER_SIZE)
        output.flush()
    }

    /** Decodes the header from [input] and returns it still attached to [input] for the payload - see [DecodedEpgSnapshot]. */
    fun decodeHeader(input: InputStream): DecodedEpgSnapshot? {
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV1(in_, input)
                else -> null
            }
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun decodeV1(input: DataInputStream, rawInput: InputStream): DecodedEpgSnapshot {
        val sourceFingerprint = input.readUTF()
        val savedAtEpochMillis = input.readLong()
        input.readLong() // documentLength - unused; the payload runs to the stream's natural EOF.
        return DecodedEpgSnapshot(EpgSnapshotHeader(sourceFingerprint, savedAtEpochMillis), rawInput)
    }
}
