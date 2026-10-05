package com.uacastplayer.epg

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
        validateCollectionCounts(data)
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
    fun decode(
        input: InputStream,
        maxProgrammes: Int = XmlTvParser.MAX_PROGRAMMES,
    ): DecodedEpgSnapshot? {
        val programmeBudget = maxProgrammes.coerceIn(0, XmlTvParser.MAX_PROGRAMMES)
        return try {
            val in_ = DataInputStream(input)
            when (in_.readInt()) {
                FORMAT_VERSION -> decodeV2(in_, programmeBudget)
                FORMAT_VERSION_1 -> decodeV1(in_, input)
                else -> null
            }
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun decodeV2(input: DataInputStream, maxProgrammes: Int): DecodedEpgSnapshot.Parsed {
        val header = EpgSnapshotHeader(input.readUTF(), input.readLong())
        val truncation = EpgTruncation(
            channelsDropped = input.readBoolean(),
            programmesDropped = input.readBoolean(),
        )
        val channelCount = input.readCountField(XmlTvParser.MAX_CHANNELS)
        val channels = readChannels(input, channelCount)
        val groupCount = input.readCountField(XmlTvParser.MAX_CHANNEL_ID_POOL)
        val programmes = readProgrammeGroups(input, groupCount, maxProgrammes, channels.idPool)
        val boundedTruncation = truncation.copy(
            channelsDropped = truncation.channelsDropped || channels.truncated,
            programmesDropped = truncation.programmesDropped || programmes.truncated,
        )
        return DecodedEpgSnapshot.Parsed(
            header,
            EpgData(EpgIndex(channels.items), programmes.groups, boundedTruncation),
        )
    }

    private fun readChannels(input: DataInputStream, count: Int): RestoredChannels {
        val channels = ArrayList<EpgChannel>(count)
        val aliases = XmlTvChannelNames()
        val idPool = SnapshotChannelIdPool()
        var metadataTruncated = false
        var totalAliasRecords = 0
        repeat(count) {
            val id = idPool.intern(input.readUTF()) ?: invalidSnapshot("channel metadata exceeds parser limits")
            // Old v2 files may predate alias budgets. Consume their layout but retain bounded
            // channel metadata, just like XML parsing. Refuse a snapshot whose serialized alias
            // count exceeds the current parser's document-wide ceiling: otherwise a corrupt count
            // can force millions of readUTF allocations on startup even though nearly all aliases
            // would be discarded by XmlTvChannelNames.
            val aliasCount = input.readCountField(XmlTvChannelNames.MAX_TOTAL_NAMES - totalAliasRecords)
            totalAliasRecords += aliasCount
            aliases.beginChannel()
            repeat(aliasCount) { aliases.add(input.readUTF().take(XmlTvParser.MAX_TEXT_LENGTH)) }
            val rawIconUrl = input.readNullableUTF()
            val iconUrl = rawIconUrl?.takeIf(idPool::retainMetadata)
            if (rawIconUrl != null && iconUrl == null) metadataTruncated = true
            channels += EpgChannel(id, aliases.finishChannel(), iconUrl)
        }
        return RestoredChannels(channels, idPool, aliases.limited || metadataTruncated)
    }

    private fun readProgrammeGroups(
        input: DataInputStream,
        groupCount: Int,
        maxProgrammes: Int,
        idPool: SnapshotChannelIdPool,
    ): RestoredProgrammeGroups {
        val groups = LinkedHashMap<String, List<EpgProgramme>>(groupCount)
        var totalProgrammes = 0
        var retainedProgrammes = 0
        var budgetExceeded = false
        repeat(groupCount) {
            val channelId = idPool.intern(input.readUTF()) ?: invalidSnapshot("channel metadata exceeds parser limits")
            if (groups.containsKey(channelId)) invalidSnapshot("duplicate programme group")
            val programmeCount = input.readCountField(XmlTvParser.MAX_PROGRAMMES)
            if (programmeCount > XmlTvParser.MAX_PROGRAMMES - totalProgrammes) {
                invalidSnapshot("total programme count exceeds parser limit")
            }
            totalProgrammes += programmeCount
            val retainedInGroup = minOf(programmeCount, maxProgrammes - retainedProgrammes)
            val programmes = ArrayList<EpgProgramme>(retainedInGroup)
            repeat(programmeCount) {
                val startMillis = input.readLong()
                val stopMillis = input.readLong()
                val title = input.readUTF()
                if (retainedProgrammes < maxProgrammes) {
                    programmes += EpgProgramme(
                        // The group's own id instance, not a fresh read per programme - one String
                        // for a channel's whole schedule instead of one per row.
                        channelId = channelId,
                        startMillis = startMillis,
                        stopMillis = stopMillis,
                        title = title.take(XmlTvParser.MAX_TEXT_LENGTH),
                    )
                    retainedProgrammes++
                } else {
                    budgetExceeded = true
                }
            }
            groups[channelId] = programmes
        }
        return RestoredProgrammeGroups(groups, budgetExceeded)
    }

    private fun invalidSnapshot(message: String): Nothing = throw IOException("Invalid EPG snapshot: $message")

    private fun validateCollectionCounts(data: EpgData) {
        if (data.index.channels.size > XmlTvParser.MAX_CHANNELS) {
            invalidSnapshot("channel count exceeds parser limit")
        }
        if (data.programmesByChannelId.size > XmlTvParser.MAX_CHANNEL_ID_POOL) {
            invalidSnapshot("group count exceeds parser limit")
        }
        var totalProgrammes = 0
        for (programmes in data.programmesByChannelId.values) {
            if (programmes.size > XmlTvParser.MAX_PROGRAMMES - totalProgrammes) {
                invalidSnapshot("programme count exceeds parser limit")
            }
            totalProgrammes += programmes.size
        }
    }

    private data class RestoredChannels(
        val items: List<EpgChannel>,
        val idPool: SnapshotChannelIdPool,
        val truncated: Boolean,
    )

    private data class RestoredProgrammeGroups(
        val groups: Map<String, List<EpgProgramme>>,
        val truncated: Boolean,
    )

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

/** Mirrors XMLTV parser's channel-id pool and aggregate ID/icon budget when restoring old caches. */
private class SnapshotChannelIdPool {
    private val ids = HashMap<String, String>()
    private var retainedMetadataChars = 0

    fun intern(value: String): String? = when {
        value.isEmpty() || value.length > XmlTvParser.MAX_ATTRIBUTE_LENGTH -> null
        ids.containsKey(value) -> ids.getValue(value)
        ids.size >= XmlTvParser.MAX_CHANNEL_ID_POOL || !retainMetadata(value) -> null
        else -> value.also { ids[value] = it }
    }

    fun retainMetadata(value: String): Boolean {
        val accepted = value.length <= XmlTvParser.MAX_ATTRIBUTE_LENGTH &&
            retainedMetadataChars <= XmlTvParser.MAX_CHANNEL_METADATA_CHARS - value.length
        if (accepted) retainedMetadataChars += value.length
        return accepted
    }
}
