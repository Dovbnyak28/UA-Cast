package com.uacastplayer.playlist

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.io.presizeFor
import com.uacastplayer.core.io.readCountField
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

    /**
     * Whether [input] was written by a build newer than this one.
     *
     * Worth asking separately from [decode], because the two unreadable cases need opposite
     * handling and [decode] cannot express that. A corrupt or truncated file is worth nothing and
     * overwriting it loses nothing; a file from a *newer* format holds every saved playlist of
     * someone who has just rolled back a release, and overwriting that with this build's format
     * would destroy them permanently - re-upgrading afterwards would find the older file.
     *
     * `FORMAT_VERSION` is 1 today, so nothing reaches this yet. It is here because the moment it
     * can happen is the moment the version is bumped, which is exactly when nobody is thinking
     * about the build that will be rolled back to.
     */
    fun isFromANewerFormat(input: InputStream): Boolean = try {
        DataInputStream(input).readInt() > FORMAT_VERSION
    } catch (_: IOException) {
        false
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
        // Not bounded by PlaylistSourcePolicy.MAX_SOURCES: lowering that later would make this
        // refuse a file it wrote itself, and the failure here costs the user every saved playlist
        // rather than a refetch. Checked for sense, grown rather than sized.
        val count = input.readCountField()
        val sources = ArrayList<PlaylistSource>(presizeFor(count))
        repeat(count) {
            val id = input.readUTF()
            val type = runCatchingNonFatal { PlaylistSourceType.valueOf(input.readUTF()) }
                .getOrDefault(PlaylistSourceType.URL)
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
