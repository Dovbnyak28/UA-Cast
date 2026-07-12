package com.uacastplayer.proxy

enum class TsCodec { MPEG2_VIDEO, H264, HEVC, MPEG_AUDIO, AAC, AC3, EAC3, UNKNOWN }

data class TsStreamInfo(val videoCodec: TsCodec?, val audioCodec: TsCodec?)

/**
 * A minimal MPEG-TS PAT/PMT reader used purely for diagnostics: it looks at the first segment of
 * a stream to answer "what codecs is this actually carrying", not to demux/decode anything. Only
 * handles PAT/PMT sections that fit in a single 188-byte packet, which covers virtually every
 * real broadcast (these tables are tiny and never intentionally split).
 */
object MpegTsSniffer {

    private const val PACKET_SIZE = 188
    private const val SYNC_BYTE = 0x47
    private const val PAT_PID = 0x0000

    fun sniff(bytes: ByteArray): TsStreamInfo? {
        val packets = splitPackets(bytes)
        if (packets.isEmpty()) return null

        val pmtPid = packets.firstNotNullOfOrNull { packet ->
            if (pidOf(packet) == PAT_PID) parsePatFirstProgramPid(packet) else null
        } ?: return null

        val pmtPacket = packets.firstOrNull { pidOf(it) == pmtPid } ?: return null
        val streamTypes = parsePmtStreamTypes(pmtPacket)
        if (streamTypes.isEmpty()) return null

        var videoCodec: TsCodec? = null
        var audioCodec: TsCodec? = null
        for (streamType in streamTypes) {
            when (categoryOf(streamType)) {
                StreamCategory.VIDEO -> if (videoCodec == null) videoCodec = codecFor(streamType)
                StreamCategory.AUDIO -> if (audioCodec == null) audioCodec = codecFor(streamType)
                StreamCategory.OTHER -> Unit
            }
        }
        return TsStreamInfo(videoCodec, audioCodec)
    }

    private fun splitPackets(bytes: ByteArray): List<ByteArray> {
        if (bytes.size < PACKET_SIZE) return emptyList()
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + PACKET_SIZE <= bytes.size) {
            if ((bytes[offset].toInt() and 0xFF) == SYNC_BYTE) {
                packets += bytes.copyOfRange(offset, offset + PACKET_SIZE)
                offset += PACKET_SIZE
            } else {
                offset++
            }
        }
        return packets
    }

    private fun pidOf(packet: ByteArray): Int {
        val b1 = packet[1].toInt() and 0xFF
        val b2 = packet[2].toInt() and 0xFF
        return ((b1 and 0x1F) shl 8) or b2
    }

    /** Returns the section payload with the adaptation field and pointer_field already skipped. */
    private fun sectionPayload(packet: ByteArray): ByteArray? {
        val adaptationFieldControl = (packet[3].toInt() shr 4) and 0x03
        var offset = 4
        when (adaptationFieldControl) {
            0b10 -> return null // adaptation field only, no payload
            0b11 -> {
                val adaptationLength = packet[offset].toInt() and 0xFF
                offset += 1 + adaptationLength
            }
            0b01 -> Unit // payload only
            else -> return null // reserved / invalid
        }
        if (offset >= packet.size) return null
        val pointerField = packet[offset].toInt() and 0xFF
        offset += 1 + pointerField
        if (offset >= packet.size) return null
        return packet.copyOfRange(offset, packet.size)
    }

    private fun sectionLength(section: ByteArray): Int {
        val b1 = section[1].toInt() and 0xFF
        val b2 = section[2].toInt() and 0xFF
        return ((b1 and 0x0F) shl 8) or b2
    }

    private fun parsePatFirstProgramPid(packet: ByteArray): Int? {
        val section = sectionPayload(packet) ?: return null
        if (section.isEmpty() || (section[0].toInt() and 0xFF) != 0x00) return null
        val length = sectionLength(section)
        val programsStart = 8
        val programsEnd = 3 + length - 4 // exclude trailing CRC32
        var offset = programsStart
        while (offset + 4 <= programsEnd && offset + 4 <= section.size) {
            val programNumber = ((section[offset].toInt() and 0xFF) shl 8) or (section[offset + 1].toInt() and 0xFF)
            val pid = ((section[offset + 2].toInt() and 0x1F) shl 8) or (section[offset + 3].toInt() and 0xFF)
            if (programNumber != 0) return pid
            offset += 4
        }
        return null
    }

    private fun parsePmtStreamTypes(packet: ByteArray): List<Int> {
        val section = sectionPayload(packet) ?: return emptyList()
        if (section.isEmpty() || (section[0].toInt() and 0xFF) != 0x02) return emptyList()
        val length = sectionLength(section)
        val programInfoLength = ((section[10].toInt() and 0x0F) shl 8) or (section[11].toInt() and 0xFF)
        var offset = 12 + programInfoLength
        val sectionEnd = 3 + length - 4 // exclude trailing CRC32
        val streamTypes = mutableListOf<Int>()
        while (offset + 5 <= sectionEnd && offset + 5 <= section.size) {
            val streamType = section[offset].toInt() and 0xFF
            val esInfoLength = ((section[offset + 3].toInt() and 0x0F) shl 8) or (section[offset + 4].toInt() and 0xFF)
            streamTypes += streamType
            offset += 5 + esInfoLength
        }
        return streamTypes
    }

    private enum class StreamCategory { VIDEO, AUDIO, OTHER }

    private fun categoryOf(streamType: Int): StreamCategory = when (streamType) {
        0x01, 0x02, 0x1B, 0x24 -> StreamCategory.VIDEO
        0x03, 0x04, 0x0F, 0x11, 0x81, 0x87 -> StreamCategory.AUDIO
        else -> StreamCategory.OTHER
    }

    private fun codecFor(streamType: Int): TsCodec = when (streamType) {
        0x01, 0x02 -> TsCodec.MPEG2_VIDEO
        0x1B -> TsCodec.H264
        0x24 -> TsCodec.HEVC
        0x03, 0x04 -> TsCodec.MPEG_AUDIO
        0x0F, 0x11 -> TsCodec.AAC
        0x81 -> TsCodec.AC3
        0x87 -> TsCodec.EAC3
        else -> TsCodec.UNKNOWN
    }
}
