package com.uacastplayer.cast

/** A single elementary stream's codec, as declared by its MPEG-TS PMT stream_type - not verified
 * against the actual bitstream, since this is a pre-flight check, not a real demuxer. */
sealed class VideoCodec {
    data object H264 : VideoCodec()
    data object Hevc : VideoCodec()
    data object Mpeg2Video : VideoCodec()
    data class Unknown(val streamType: Int) : VideoCodec()
}

sealed class AudioCodec {
    data object Aac : AudioCodec()
    data object AacLatm : AudioCodec()

    /** PMT stream_type 0x03/0x04 ("MPEG-1/2 audio") doesn't distinguish MP2 from MP3 - both use
     * the same stream_type, and telling them apart needs an actual bitstream parse, not a PMT
     * read. Chromecast's Default Receiver officially plays MP3, and real receivers overwhelmingly
     * play MP2 too, so this is never treated as a hard incompatibility (see
     * [CastCompatibilityPolicy]) - only named in a post-failure message if casting doesn't pan out. */
    data object MpegAudio : AudioCodec()
    data object Ac3 : AudioCodec()
    data object Eac3 : AudioCodec()
    data class Unknown(val streamType: Int) : AudioCodec()
}

/** [videoCodec] is the first video-category elementary stream found (a program has at most one in
 * practice); [audioCodecs] lists every audio-category elementary stream (IPTV feeds commonly carry
 * more than one language/format track). */
data class TsProgramInfo(val videoCodec: VideoCodec?, val audioCodecs: List<AudioCodec>)

private const val PACKET_SIZE = 188
private const val SYNC_BYTE = 0x47
private const val PAT_PID = 0x0000

/**
 * Minimal MPEG-TS PAT/PMT reader: walks PAT -> PMT to find every elementary stream's declared
 * codec, used as a pre-flight compatibility check before ever loading a channel onto a Cast
 * receiver (see [CastCompatibilityPolicy]) and as the "is this stream actually playable as-is"
 * gate for the proxy's raw-TS-to-HLS remux. Only handles PAT/PMT sections that fit in a single
 * 188-byte packet - true for virtually every real broadcast, since these tables are deliberately
 * tiny; a stream that splits them across packets (or omits them from the probed prefix) yields
 * null rather than a crash, and callers treat that the same as "couldn't determine the codecs".
 */
object TsProgramInfoParser {

    fun parse(bytes: ByteArray): TsProgramInfo? {
        val packets = splitPackets(bytes)
        if (packets.isEmpty()) return null

        val pmtPid = packets.firstNotNullOfOrNull { packet ->
            if (pidOf(packet) == PAT_PID) parsePatFirstProgramPid(packet) else null
        } ?: return null

        val pmtPacket = packets.firstOrNull { pidOf(it) == pmtPid } ?: return null
        val streamTypes = parsePmtStreamTypes(pmtPacket)
        if (streamTypes.isEmpty()) return null

        var videoCodec: VideoCodec? = null
        val audioCodecs = mutableListOf<AudioCodec>()
        for (streamType in streamTypes) {
            when (categoryOf(streamType)) {
                StreamCategory.VIDEO -> if (videoCodec == null) videoCodec = videoCodecFor(streamType)
                StreamCategory.AUDIO -> audioCodecs += audioCodecFor(streamType)
                StreamCategory.OTHER -> Unit
            }
        }
        return TsProgramInfo(videoCodec, audioCodecs)
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
        if (section.size < 3) return 0
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
        // Must fit the fixed PMT header through program_info_length (bytes 0-11, see below) before
        // any of it can be read - a truncated/malformed section must degrade to "no streams found",
        // never throw, since this reads bytes from an arbitrary upstream server.
        if (section.size < 12 || (section[0].toInt() and 0xFF) != 0x02) return emptyList()
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

    // A handful of additional real-world video/audio stream_types beyond the ones this app can
    // actually name a codec for, so those still route to Unknown(code) instead of being dropped
    // as OTHER (which is reserved for genuinely non-AV streams: subtitles, private data, etc.).
    private val VIDEO_STREAM_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x20, 0x24, 0x42)
    private val AUDIO_STREAM_TYPES = setOf(0x03, 0x04, 0x06, 0x0F, 0x11, 0x1C, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x8A)

    private fun categoryOf(streamType: Int): StreamCategory = when {
        streamType in VIDEO_STREAM_TYPES -> StreamCategory.VIDEO
        streamType in AUDIO_STREAM_TYPES -> StreamCategory.AUDIO
        else -> StreamCategory.OTHER
    }

    private fun videoCodecFor(streamType: Int): VideoCodec = when (streamType) {
        0x01, 0x02 -> VideoCodec.Mpeg2Video
        0x1B -> VideoCodec.H264
        0x24 -> VideoCodec.Hevc
        else -> VideoCodec.Unknown(streamType)
    }

    private fun audioCodecFor(streamType: Int): AudioCodec = when (streamType) {
        0x03, 0x04 -> AudioCodec.MpegAudio
        0x0F -> AudioCodec.Aac
        0x11 -> AudioCodec.AacLatm
        0x81 -> AudioCodec.Ac3
        0x87 -> AudioCodec.Eac3
        else -> AudioCodec.Unknown(streamType)
    }
}
