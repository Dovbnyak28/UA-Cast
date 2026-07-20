package com.uacastplayer.cast

/**
 * Human-readable names for the codecs [TsProgramInfoParser] can identify, for the "this channel
 * uses X, which Chromecast doesn't support" message (see [CodecIncompatibility]) - naming the
 * actual codec is far more useful to a user than a generic "unsupported format".
 */
object CodecDisplayName {

    fun of(codec: VideoCodec): String = when (codec) {
        VideoCodec.H264 -> "H.264"
        VideoCodec.Hevc -> "HEVC"
        VideoCodec.Mpeg2Video -> "MPEG-2"
        is VideoCodec.Unknown -> "video (type ${codec.streamType})"
    }

    fun of(codec: AudioCodec): String = when (codec) {
        AudioCodec.Aac, AudioCodec.AacLatm -> "AAC"
        AudioCodec.MpegAudio -> "MP2"
        AudioCodec.Ac3 -> "AC-3"
        AudioCodec.Eac3 -> "E-AC-3"
        is AudioCodec.Unknown -> "audio (type ${codec.streamType})"
    }
}
