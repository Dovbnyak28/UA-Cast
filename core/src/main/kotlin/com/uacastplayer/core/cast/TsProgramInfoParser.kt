package com.uacastplayer.core.cast

/** A single elementary stream's codec, as declared by its MPEG-TS PMT stream_type - not verified
 * against the actual bitstream, since this is a pre-flight check, not a real demuxer. */
sealed interface VideoCodec {
    data object H264 : VideoCodec
    data object Hevc : VideoCodec
    data object Mpeg2Video : VideoCodec
    data class Unknown(val streamType: Int) : VideoCodec
}

sealed interface AudioCodec {
    data object Aac : AudioCodec
    data object AacLatm : AudioCodec

    /** PMT stream_type 0x03/0x04 ("MPEG-1/2 audio") doesn't distinguish MP2 from MP3 - both use
     * the same stream_type, and telling them apart needs an actual bitstream parse, not a PMT
     * read. Chromecast's Default Receiver officially plays MP3, and real receivers overwhelmingly
     * play MP2 too, so this is never treated as a hard incompatibility (see
     * [CastCompatibilityPolicy]) - only named in a post-failure message if casting doesn't pan out. */
    data object MpegAudio : AudioCodec
    data object Ac3 : AudioCodec
    data object Eac3 : AudioCodec
    data class Unknown(val streamType: Int) : AudioCodec
}

/** [videoCodec] is the first video-category elementary stream found (a program has at most one in
 * practice); [audioCodecs] lists every audio-category elementary stream (IPTV feeds commonly carry
 * more than one language/format track). */
data class TsProgramInfo(val videoCodec: VideoCodec?, val audioCodecs: List<AudioCodec>)

private const val PACKET_SIZE = 188
private const val SYNC_BYTE = 0x47
private const val PAT_PID = 0x0000
private const val BYTE_MASK = 0xFF
private const val PID_HIGH_MASK = 0x1F
private const val BYTE_SHIFT = 8
private const val ADAPTATION_FIELD_CONTROL_OFFSET = 3
private const val ADAPTATION_FIELD_CONTROL_SHIFT = 4
private const val ADAPTATION_FIELD_CONTROL_MASK = 0x03
private const val ADAPTATION_AND_PAYLOAD = 0b11
private const val PAYLOAD_ONLY = 0b01
private const val TS_HEADER_SIZE = 4
private const val ADAPTATION_PAYLOAD_PREFIX_SIZE = 5
private const val SECTION_HEADER_SIZE = 3
private const val SECTION_LENGTH_HIGH_MASK = 0x0F
private const val CRC_SIZE = 4
private const val PAT_TABLE_ID = 0x00
private const val PAT_PROGRAMS_OFFSET = 8
private const val PAT_PROGRAM_ENTRY_SIZE = 4
private const val PAT_PROGRAM_NUMBER_LOW_OFFSET = 1
private const val PAT_PID_HIGH_OFFSET = 2
private const val PAT_PID_LOW_OFFSET = 3
private const val PMT_TABLE_ID = 0x02
private const val PMT_FIXED_HEADER_SIZE = 12
private const val PMT_PROGRAM_INFO_LENGTH_HIGH_OFFSET = 10
private const val PMT_PROGRAM_INFO_LENGTH_LOW_OFFSET = 11
private const val PMT_ES_ENTRY_HEADER_SIZE = 5
private const val PMT_ES_INFO_LENGTH_HIGH_OFFSET = 3
private const val PMT_ES_INFO_LENGTH_LOW_OFFSET = 4

private const val STREAM_TYPE_MPEG1_VIDEO = 0x01
private const val STREAM_TYPE_MPEG2_VIDEO = 0x02
private const val STREAM_TYPE_MPEG1_AUDIO = 0x03
private const val STREAM_TYPE_MPEG2_AUDIO = 0x04
private const val STREAM_TYPE_PRIVATE_DATA = 0x06
private const val STREAM_TYPE_AAC = 0x0F
private const val STREAM_TYPE_MPEG4_VIDEO = 0x10
private const val STREAM_TYPE_AAC_LATM = 0x11
private const val STREAM_TYPE_H264 = 0x1B
private const val STREAM_TYPE_MPEG4_AUDIO = 0x1C
private const val STREAM_TYPE_MVC = 0x20
private const val STREAM_TYPE_HEVC = 0x24
private const val STREAM_TYPE_AVS = 0x42
private const val STREAM_TYPE_AC3 = 0x81
private const val STREAM_TYPE_PRIVATE_82 = 0x82
private const val STREAM_TYPE_PRIVATE_83 = 0x83
private const val STREAM_TYPE_PRIVATE_84 = 0x84
private const val STREAM_TYPE_PRIVATE_85 = 0x85
private const val STREAM_TYPE_PRIVATE_86 = 0x86
private const val STREAM_TYPE_EAC3 = 0x87
private const val STREAM_TYPE_DTS = 0x8A

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
        val pmtPid = packets.firstNotNullOfOrNull { packet ->
            if (pidOf(packet) == PAT_PID) parsePatFirstProgramPid(packet) else null
        }
        val streamTypes = pmtPid
            ?.let { expectedPid -> packets.firstOrNull { pidOf(it) == expectedPid } }
            ?.let(::parsePmtStreamTypes)
        return streamTypes?.takeIf { it.isNotEmpty() }?.let(::programInfoFor)
    }

    private fun programInfoFor(streamTypes: List<Int>): TsProgramInfo {
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
            if ((bytes[offset].toInt() and BYTE_MASK) == SYNC_BYTE) {
                packets += bytes.copyOfRange(offset, offset + PACKET_SIZE)
                offset += PACKET_SIZE
            } else {
                offset++
            }
        }
        return packets
    }

    private fun pidOf(packet: ByteArray): Int {
        val b1 = packet[1].toInt() and BYTE_MASK
        val b2 = packet[2].toInt() and BYTE_MASK
        return ((b1 and PID_HIGH_MASK) shl BYTE_SHIFT) or b2
    }

    /** Returns the section payload with the adaptation field and pointer_field already skipped. */
    private fun sectionPayload(packet: ByteArray): ByteArray? {
        val adaptationFieldControl =
            (packet[ADAPTATION_FIELD_CONTROL_OFFSET].toInt() shr ADAPTATION_FIELD_CONTROL_SHIFT) and
                ADAPTATION_FIELD_CONTROL_MASK
        val payloadStart = when (adaptationFieldControl) {
            ADAPTATION_AND_PAYLOAD ->
                ADAPTATION_PAYLOAD_PREFIX_SIZE + (packet[TS_HEADER_SIZE].toInt() and BYTE_MASK)
            PAYLOAD_ONLY -> TS_HEADER_SIZE
            else -> null
        }
        return payloadStart
            ?.takeIf { it < packet.size }
            ?.let { start -> start + 1 + (packet[start].toInt() and BYTE_MASK) }
            ?.takeIf { it < packet.size }
            ?.let { packet.copyOfRange(it, packet.size) }
    }

    private fun sectionLength(section: ByteArray): Int {
        if (section.size < SECTION_HEADER_SIZE) return 0
        val b1 = section[1].toInt() and BYTE_MASK
        val b2 = section[2].toInt() and BYTE_MASK
        return ((b1 and SECTION_LENGTH_HIGH_MASK) shl BYTE_SHIFT) or b2
    }

    private fun parsePatFirstProgramPid(packet: ByteArray): Int? {
        val section = sectionPayload(packet)
            ?.takeIf { it.isNotEmpty() && (it[0].toInt() and BYTE_MASK) == PAT_TABLE_ID }
        return section?.let(::firstProgramPid)
    }

    private fun firstProgramPid(section: ByteArray): Int? {
        val length = sectionLength(section)
        val programsStart = PAT_PROGRAMS_OFFSET
        val programsEnd = SECTION_HEADER_SIZE + length - CRC_SIZE
        var offset = programsStart
        while (offset + PAT_PROGRAM_ENTRY_SIZE <= programsEnd &&
            offset + PAT_PROGRAM_ENTRY_SIZE <= section.size
        ) {
            val programNumber = ((section[offset].toInt() and BYTE_MASK) shl BYTE_SHIFT) or
                (section[offset + PAT_PROGRAM_NUMBER_LOW_OFFSET].toInt() and BYTE_MASK)
            val pid = (
                (section[offset + PAT_PID_HIGH_OFFSET].toInt() and PID_HIGH_MASK) shl BYTE_SHIFT
                ) or (section[offset + PAT_PID_LOW_OFFSET].toInt() and BYTE_MASK)
            if (programNumber != 0) return pid
            offset += PAT_PROGRAM_ENTRY_SIZE
        }
        return null
    }

    private fun parsePmtStreamTypes(packet: ByteArray): List<Int> {
        // Must fit the fixed PMT header through program_info_length (bytes 0-11, see below) before
        // any of it can be read - a truncated/malformed section must degrade to "no streams found",
        // never throw, since this reads bytes from an arbitrary upstream server.
        val section = sectionPayload(packet)
            ?.takeIf { it.size >= PMT_FIXED_HEADER_SIZE && (it[0].toInt() and BYTE_MASK) == PMT_TABLE_ID }
        return section?.let(::pmtStreamTypes).orEmpty()
    }

    private fun pmtStreamTypes(section: ByteArray): List<Int> {
        val length = sectionLength(section)
        val programInfoLength = (
            (section[PMT_PROGRAM_INFO_LENGTH_HIGH_OFFSET].toInt() and SECTION_LENGTH_HIGH_MASK) shl BYTE_SHIFT
            ) or (section[PMT_PROGRAM_INFO_LENGTH_LOW_OFFSET].toInt() and BYTE_MASK)
        var offset = PMT_FIXED_HEADER_SIZE + programInfoLength
        val sectionEnd = SECTION_HEADER_SIZE + length - CRC_SIZE
        val streamTypes = mutableListOf<Int>()
        while (offset + PMT_ES_ENTRY_HEADER_SIZE <= sectionEnd &&
            offset + PMT_ES_ENTRY_HEADER_SIZE <= section.size
        ) {
            val streamType = section[offset].toInt() and BYTE_MASK
            val esInfoLength = (
                (section[offset + PMT_ES_INFO_LENGTH_HIGH_OFFSET].toInt() and SECTION_LENGTH_HIGH_MASK) shl
                    BYTE_SHIFT
                ) or (section[offset + PMT_ES_INFO_LENGTH_LOW_OFFSET].toInt() and BYTE_MASK)
            streamTypes += streamType
            offset += PMT_ES_ENTRY_HEADER_SIZE + esInfoLength
        }
        return streamTypes
    }

    private enum class StreamCategory { VIDEO, AUDIO, OTHER }

    // A handful of additional real-world video/audio stream_types beyond the ones this app can
    // actually name a codec for, so those still route to Unknown(code) instead of being dropped
    // as OTHER (which is reserved for genuinely non-AV streams: subtitles, private data, etc.).
    private val VIDEO_STREAM_TYPES = setOf(
        STREAM_TYPE_MPEG1_VIDEO,
        STREAM_TYPE_MPEG2_VIDEO,
        STREAM_TYPE_MPEG4_VIDEO,
        STREAM_TYPE_H264,
        STREAM_TYPE_MVC,
        STREAM_TYPE_HEVC,
        STREAM_TYPE_AVS,
    )
    private val AUDIO_STREAM_TYPES = setOf(
        STREAM_TYPE_MPEG1_AUDIO,
        STREAM_TYPE_MPEG2_AUDIO,
        STREAM_TYPE_PRIVATE_DATA,
        STREAM_TYPE_AAC,
        STREAM_TYPE_AAC_LATM,
        STREAM_TYPE_MPEG4_AUDIO,
        STREAM_TYPE_AC3,
        STREAM_TYPE_PRIVATE_82,
        STREAM_TYPE_PRIVATE_83,
        STREAM_TYPE_PRIVATE_84,
        STREAM_TYPE_PRIVATE_85,
        STREAM_TYPE_PRIVATE_86,
        STREAM_TYPE_EAC3,
        STREAM_TYPE_DTS,
    )

    private fun categoryOf(streamType: Int): StreamCategory = when {
        streamType in VIDEO_STREAM_TYPES -> StreamCategory.VIDEO
        streamType in AUDIO_STREAM_TYPES -> StreamCategory.AUDIO
        else -> StreamCategory.OTHER
    }

    private fun videoCodecFor(streamType: Int): VideoCodec = when (streamType) {
        STREAM_TYPE_MPEG1_VIDEO, STREAM_TYPE_MPEG2_VIDEO -> VideoCodec.Mpeg2Video
        STREAM_TYPE_H264 -> VideoCodec.H264
        STREAM_TYPE_HEVC -> VideoCodec.Hevc
        else -> VideoCodec.Unknown(streamType)
    }

    private fun audioCodecFor(streamType: Int): AudioCodec = when (streamType) {
        STREAM_TYPE_MPEG1_AUDIO, STREAM_TYPE_MPEG2_AUDIO -> AudioCodec.MpegAudio
        STREAM_TYPE_AAC -> AudioCodec.Aac
        STREAM_TYPE_AAC_LATM -> AudioCodec.AacLatm
        STREAM_TYPE_AC3 -> AudioCodec.Ac3
        STREAM_TYPE_EAC3 -> AudioCodec.Eac3
        else -> AudioCodec.Unknown(streamType)
    }
}
