package com.uacastplayer.player

enum class AudioChannelLayout { MONO, STEREO, SURROUND_5_1, SURROUND_7_1, OTHER }

/** Badges derived from the actively playing track's *measured* format - never guessed. */
object PlaybackBadges {

    fun qualityLabel(height: Int): String? = when {
        height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
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
        1 -> AudioChannelLayout.MONO
        2 -> AudioChannelLayout.STEREO
        6 -> AudioChannelLayout.SURROUND_5_1
        8 -> AudioChannelLayout.SURROUND_7_1
        else -> AudioChannelLayout.OTHER
    }
}
