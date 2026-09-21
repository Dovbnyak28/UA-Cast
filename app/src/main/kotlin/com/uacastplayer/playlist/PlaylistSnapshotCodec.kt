package com.uacastplayer.playlist

import com.uacastplayer.core.io.presizeFor
import com.uacastplayer.core.io.readCountField
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-rolled versioned binary (de)serializer for [PlaylistSnapshot]. Bumping [FORMAT_VERSION]
 * and adding a new `when` branch in [decode] is the expected way to evolve the format; unknown
 * versions decode to `null` (treated by callers as "no usable cache") rather than throwing. Old
 * versions stay readable (see [decodeV1]) so an app update doesn't discard an existing on-disk
 * snapshot just because the format grew a field.
 */
object PlaylistSnapshotCodec {

    private const val FORMAT_VERSION = 2
    private const val FORMAT_VERSION_1 = 1

    fun encode(snapshot: PlaylistSnapshot, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(snapshot.sourceFingerprint)
        out.writeNullableUTF(snapshot.sourceUrl)
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
            out.writeNullableUTF(channel.userAgent)
            out.writeNullableUTF(channel.referrer)
        }
        out.flush()
    }

    fun decode(input: InputStream): PlaylistSnapshot? {
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV2(in_)
                FORMAT_VERSION_1 -> decodeV1(in_)
                else -> null
            }
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun decodeV2(input: DataInputStream): PlaylistSnapshot {
        val sourceFingerprint = input.readUTF()
        val sourceUrl = input.readNullableUTF()
        val savedAtEpochMillis = input.readLong()
        val skippedLineCount = input.readCountField()
        // No ceiling to check against - a playlist is as long as the provider makes it, and the
        // only bound is PlaylistUrlLoader's 8MB on the document. So the count is checked for sense
        // and the list is grown rather than sized from the file; see readCountField for why a
        // count invented here would be worse than none.
        val channelCount = input.readCountField()
        val channels = ArrayList<M3uChannel>(presizeFor(channelCount))
        repeat(channelCount) {
            val displayName = input.readUTF()
            val streamUrl = input.readUTF()
            val tvgId = input.readNullableUTF()
            val tvgName = input.readNullableUTF()
            val tvgLogo = input.readNullableUTF()
            val groupTitle = input.readNullableUTF()
            val userAgent = input.readNullableUTF()
            val referrer = input.readNullableUTF()
            channels += M3uChannel(displayName, streamUrl, tvgId, tvgName, tvgLogo, groupTitle, userAgent, referrer)
        }
        return PlaylistSnapshot(sourceFingerprint, savedAtEpochMillis, channels, skippedLineCount, sourceUrl)
    }

    /** Pre-sourceUrl, pre-per-channel-headers format. */
    private fun decodeV1(input: DataInputStream): PlaylistSnapshot {
        val sourceFingerprint = input.readUTF()
        val savedAtEpochMillis = input.readLong()
        val skippedLineCount = input.readCountField()
        // No ceiling to check against - a playlist is as long as the provider makes it, and the
        // only bound is PlaylistUrlLoader's 8MB on the document. So the count is checked for sense
        // and the list is grown rather than sized from the file; see readCountField for why a
        // count invented here would be worse than none.
        val channelCount = input.readCountField()
        val channels = ArrayList<M3uChannel>(presizeFor(channelCount))
        repeat(channelCount) {
            val displayName = input.readUTF()
            val streamUrl = input.readUTF()
            val tvgId = input.readNullableUTF()
            val tvgName = input.readNullableUTF()
            val tvgLogo = input.readNullableUTF()
            val groupTitle = input.readNullableUTF()
            channels += M3uChannel(displayName, streamUrl, tvgId, tvgName, tvgLogo, groupTitle)
        }
        return PlaylistSnapshot(sourceFingerprint, savedAtEpochMillis, channels, skippedLineCount, sourceUrl = null)
    }

    private fun DataOutputStream.writeNullableUTF(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUTF(): String? {
        return if (readBoolean()) readUTF() else null
    }
}
