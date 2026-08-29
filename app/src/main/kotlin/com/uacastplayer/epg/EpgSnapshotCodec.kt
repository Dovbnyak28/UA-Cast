package com.uacastplayer.epg

import com.uacastplayer.core.io.presizeFor
import com.uacastplayer.core.io.readCountField
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * What came back off disk: either the parsed guide (v2, the format written today) or the raw XMLTV
 * document a previous version cached (v1).
 */
sealed interface DecodedEpgSnapshot {
    val header: EpgSnapshotHeader

    /** Ready to use as-is - no XML, no gzip, no parse. */
    data class Parsed(override val header: EpgSnapshotHeader, val data: EpgData) : DecodedEpgSnapshot

    /**
     * A v1 snapshot. [documentStream] is the decode input itself, left positioned right after the
     * header so the caller can parse straight off it (nothing follows the payload, so reading to
     * EOF is safe) without this codec ever buffering the document. The caller closes it.
     */
    data class Document(
        override val header: EpgSnapshotHeader,
        val documentStream: InputStream,
    ) : DecodedEpgSnapshot
}

/**
 * Versioned binary (de)serializer for the cached EPG.
 *
 * **v2 stores the parsed guide; v1 stored the raw XMLTV document.** That change is the whole point
 * of the format bump. Keeping the document meant every cold start re-inflated and re-parsed it to
 * rebuild data the app had already had the previous time: measured on a real device against a real
 * feed, restoring a 44.9MB snapshot took **53 seconds** of background CPU to produce 676 channels
 * and 250,000 programmes. Nothing reads the raw XML any more - `<desc>` was dropped from
 * [EpgProgramme] precisely because nothing ever displayed it - so the document was being carried at
 * full price for no reader.
 *
 * The layout leans on the shape the data already has: programmes are grouped by channel id, so each
 * id is written once per group rather than once per programme, and on decode every programme in a
 * group shares that one [String] instance. That is the same channel-id pooling the in-memory
 * representation relies on, preserved across the file rather than rebuilt on load.
 *
 * v1 is still readable so that upgrading does not throw away a guide the user already has - it is
 * parsed exactly as before, once, and [com.uacastplayer.data.epg.EpgRepository] immediately
 * rewrites it as v2 so that penalty is paid on one launch and never again.
 */
object EpgSnapshotCodec {

    private const val FORMAT_VERSION = 2
    private const val FORMAT_VERSION_1 = 1

    fun encode(header: EpgSnapshotHeader, data: EpgData, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(FORMAT_VERSION)
        out.writeUTF(header.sourceFingerprint)
        out.writeLong(header.savedAtEpochMillis)
        out.writeBoolean(data.truncation.channelsDropped)
        out.writeBoolean(data.truncation.programmesDropped)

        out.writeInt(data.index.channels.size)
        for (channel in data.index.channels) {
            out.writeUTF(channel.id)
            out.writeInt(channel.displayNames.size)
            for (name in channel.displayNames) out.writeUTF(name)
            out.writeNullableUTF(channel.iconUrl)
        }

        out.writeInt(data.programmesByChannelId.size)
        for ((channelId, programmes) in data.programmesByChannelId) {
            // Written once for the whole group - the per-programme channelId is implied by it, and
            // is what the decoder hands back to every programme in the group as one shared String.
            out.writeUTF(channelId)
            out.writeInt(programmes.size)
            for (programme in programmes) {
                out.writeLong(programme.startMillis)
                out.writeLong(programme.stopMillis)
                out.writeUTF(programme.title)
            }
        }
        out.flush()
    }

    /**
     * Reads whichever format is on disk. A v1 result stays attached to [input] for its payload, so
     * the caller must close it; a v2 result has consumed everything it needs.
     */
    fun decode(input: InputStream): DecodedEpgSnapshot? {
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV2(in_)
                FORMAT_VERSION_1 -> decodeV1(in_, input)
                else -> null
            }
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun decodeV2(input: DataInputStream): DecodedEpgSnapshot.Parsed {
        val header = EpgSnapshotHeader(input.readUTF(), input.readLong())
        val truncation = EpgTruncation(
            channelsDropped = input.readBoolean(),
            programmesDropped = input.readBoolean(),
        )

        val channelCount = input.readCountField(XmlTvParser.MAX_CHANNELS)
        val channels = ArrayList<EpgChannel>(channelCount)
        repeat(channelCount) {
            val id = input.readUTF()
            // No domain limit exists for these - the parser keeps every <display-name> a channel
            // carries - so this is checked for sense rather than against a ceiling, and the list is
            // grown rather than sized from the file. A count that is merely huge then runs out of
            // stream within a couple of reads, which the caller's EOFException catch turns into the
            // refetch this is all for.
            val displayNameCount = input.readCountField()
            val displayNames = ArrayList<String>(presizeFor(displayNameCount))
            repeat(displayNameCount) { displayNames += input.readUTF() }
            channels += EpgChannel(id, displayNames, input.readNullableUTF())
        }

        // One group per channel id, so the channel ceiling bounds this too.
        val groupCount = input.readCountField(XmlTvParser.MAX_CHANNELS)
        val programmesByChannelId = LinkedHashMap<String, List<EpgProgramme>>(groupCount)
        repeat(groupCount) {
            val channelId = input.readUTF()
            // A single channel cannot hold more than the whole file's ceiling.
            val programmeCount = input.readCountField(XmlTvParser.MAX_PROGRAMMES)
            val programmes = ArrayList<EpgProgramme>(programmeCount)
            repeat(programmeCount) {
                programmes += EpgProgramme(
                    // The group's own id instance, not a fresh read per programme - one String for
                    // a channel's whole schedule instead of one per row.
                    channelId = channelId,
                    startMillis = input.readLong(),
                    stopMillis = input.readLong(),
                    title = input.readUTF(),
                )
            }
            programmesByChannelId[channelId] = programmes
        }

        return DecodedEpgSnapshot.Parsed(header, EpgData(EpgIndex(channels), programmesByChannelId, truncation))
    }

    private fun decodeV1(input: DataInputStream, rawInput: InputStream): DecodedEpgSnapshot.Document {
        val sourceFingerprint = input.readUTF()
        val savedAtEpochMillis = input.readLong()
        input.readLong() // documentLength - unused; the payload runs to the stream's natural EOF.
        return DecodedEpgSnapshot.Document(EpgSnapshotHeader(sourceFingerprint, savedAtEpochMillis), rawInput)
    }

    private fun DataOutputStream.writeNullableUTF(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUTF(): String? = if (readBoolean()) readUTF() else null
}
