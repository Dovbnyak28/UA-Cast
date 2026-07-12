package com.uacastplayer.playlist

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-rolled versioned binary (de)serializer for [PlaylistSnapshot]. Bumping [FORMAT_VERSION]
 * and adding a new `when` branch in [decode] is the expected way to evolve the format; unknown
 * versions decode to `null` (treated by callers as "no usable cache") rather than throwing.
 */
object PlaylistSnapshotCodec {

    private const val FORMAT_VERSION = 1

    fun encode(snapshot: PlaylistSnapshot, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(snapshot.sourceFingerprint)
        out.writeLong(snapshot.savedAtEpochMillis)
        out.writeInt(snapshot.skippedLineCount)
        out.writeInt(snapshot.channels.size)
        for (channel in snapshot.channels) {
            out.writeUTF(channel.displayName)
            out.writeUTF(channel.streamUrl)
            out.writeNullableUTF(channel.tvgId)
            out.writeNullableUTF(channel.tvgName)
            out.writeNullableUTF(channel.tvgLogo)
            out.writeNullableUTF(channel.groupTitle)
        }
        out.flush()
    }

    fun decode(input: InputStream): PlaylistSnapshot? {
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

    private fun decodeV1(input: DataInputStream): PlaylistSnapshot {
        val sourceFingerprint = input.readUTF()
        val savedAtEpochMillis = input.readLong()
        val skippedLineCount = input.readInt()
        val channelCount = input.readInt()
        val channels = ArrayList<M3uChannel>(channelCount)
        repeat(channelCount) {
            val displayName = input.readUTF()
            val streamUrl = input.readUTF()
            val tvgId = input.readNullableUTF()
            val tvgName = input.readNullableUTF()
            val tvgLogo = input.readNullableUTF()
            val groupTitle = input.readNullableUTF()
            channels += M3uChannel(displayName, streamUrl, tvgId, tvgName, tvgLogo, groupTitle)
        }
        return PlaylistSnapshot(sourceFingerprint, savedAtEpochMillis, channels, skippedLineCount)
    }

    private fun DataOutputStream.writeNullableUTF(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUTF(): String? {
        return if (readBoolean()) readUTF() else null
    }
}
