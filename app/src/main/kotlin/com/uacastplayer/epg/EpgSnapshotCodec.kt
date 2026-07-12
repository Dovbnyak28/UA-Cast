package com.uacastplayer.epg

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Versioned binary (de)serializer for [EpgSnapshot]; unknown versions decode to `null`. */
object EpgSnapshotCodec {

    private const val FORMAT_VERSION = 1

    fun encode(snapshot: EpgSnapshot, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(snapshot.sourceFingerprint)
        out.writeLong(snapshot.savedAtEpochMillis)
        out.writeInt(snapshot.gzipDocument.size)
        out.write(snapshot.gzipDocument)
        out.flush()
    }

    fun decode(input: InputStream): EpgSnapshot? {
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV1(in_)
                else -> null
            }
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun decodeV1(input: DataInputStream): EpgSnapshot {
        val sourceFingerprint = input.readUTF()
        val savedAtEpochMillis = input.readLong()
        val size = input.readInt()
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return EpgSnapshot(sourceFingerprint, savedAtEpochMillis, bytes)
    }
}
