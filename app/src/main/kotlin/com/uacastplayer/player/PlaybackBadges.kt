package com.uacastplayer.player

enum class AudioChannelLayout { MONO, STEREO, SURROUND_5_1, SURROUND_7_1, OTHER }

/** Badges derived from the actively playing track's *measured* format - never guessed. */
object PlaybackBadges {

    private const val UHD_HEIGHT = 2160
    private const val QHD_HEIGHT = 1440
    private const val FULL_HD_HEIGHT = 1080
    private const val HD_HEIGHT = 720
    private const val SD_HEIGHT = 480
    private const val MONO_CHANNEL_COUNT = 1
    private const val STEREO_CHANNEL_COUNT = 2
    private const val SURROUND_5_1_CHANNEL_COUNT = 6
    private const val SURROUND_7_1_CHANNEL_COUNT = 8

    fun qualityLabel(height: Int): String? = when {
        height <= 0 -> null
        height >= UHD_HEIGHT -> "4K"
        height >= QHD_HEIGHT -> "1440p"
        height >= FULL_HD_HEIGHT -> "1080p"
        height >= HD_HEIGHT -> "720p"
        height >= SD_HEIGHT -> "480p"
        else -> "${height}p"
    }

    fun videoCodecLabel(mimeType: String?): String? = when (mimeType?.lowercase()) {
        null -> null
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/mpeg2" -> "MPEG-2"
        else -> null
    }

    fun audioCodecLabel(mimeType: String?): String? = when (mimeType?.lowercase()) {
        null -> null
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg", "audio/mpeg-l2" -> "MP2"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/vnd.dts" -> "DTS"
        "audio/opus" -> "Opus"
        else -> null
    }

    /** Unlike [audioCodecLabel]/[videoCodecLabel], this is worth falling back to the raw MIME
     * subtype for an unmapped format rather than returning null - a subtitle track picker with no
     * other distinguishing info (no bitrate/channels/resolution) would otherwise show nothing at
     * all to tell same-language tracks apart. */
    fun textCodecLabel(mimeType: String?): String? = when (mimeType?.lowercase()) {
        null -> null
        "text/vtt" -> "WebVTT"
        "application/x-subrip", "text/x-ssa" -> "SRT"
        "application/ttml+xml" -> "TTML"
        "application/cea-608" -> "CEA-608"
        "application/cea-708" -> "CEA-708"
        "application/dvbsubs" -> "DVB"
        else -> mimeType.substringAfter('/').takeIf { it.isNotBlank() }?.uppercase()
    }

    fun channelLayout(channelCount: Int): AudioChannelLayout = when (channelCount) {
        MONO_CHANNEL_COUNT -> AudioChannelLayout.MONO
        STEREO_CHANNEL_COUNT -> AudioChannelLayout.STEREO
        SURROUND_5_1_CHANNEL_COUNT -> AudioChannelLayout.SURROUND_5_1
        SURROUND_7_1_CHANNEL_COUNT -> AudioChannelLayout.SURROUND_7_1
        else -> AudioChannelLayout.OTHER
    }
}
