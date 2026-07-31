package com.uacastplayer.playlist

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-rolled versioned binary (de)serializer for the saved playlist source list - same pattern as
 * [PlaylistSnapshotCodec]. Unknown versions decode to an empty list (treated as "no saved sources
 * yet") rather than throwing.
 */
object PlaylistSourceCodec {

    private const val FORMAT_VERSION = 1

    fun encode(sources: List<PlaylistSource>, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeInt(sources.size)
        for (source in sources) {
            out.writeUTF(source.id)
            out.writeUTF(source.type.name)
            out.writeUTF(source.location)
            out.writeNullableUTF(source.displayName)
            out.writeLong(source.addedAtEpochMillis)
        }
        out.flush()
    }

    fun decode(input: InputStream): List<PlaylistSource> {
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV1(in_)
                else -> emptyList()
            }
        } catch (_: EOFException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        }
    }

    private fun decodeV1(input: DataInputStream): List<PlaylistSource> {
        val count = input.readInt()
        val sources = ArrayList<PlaylistSource>(count)
        repeat(count) {
            val id = input.readUTF()
            val type = runCatching { PlaylistSourceType.valueOf(input.readUTF()) }.getOrDefault(PlaylistSourceType.URL)
            val location = input.readUTF()
            val displayName = input.readNullableUTF()
            val addedAtEpochMillis = input.readLong()
            sources += PlaylistSource(id, type, location, displayName, addedAtEpochMillis)
        }
        return sources
    }

    private fun DataOutputStream.writeNullableUTF(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUTF(): String? {
        return if (readBoolean()) readUTF() else null
    }
}
