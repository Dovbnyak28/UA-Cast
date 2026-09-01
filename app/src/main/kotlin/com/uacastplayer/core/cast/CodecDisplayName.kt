package com.uacastplayer.core.cast

/**
 * Human-readable names for codecs identified by [TsProgramInfoParser]. Keeping this pure
 * formatter beside the codec models lets player UI explain compatibility without depending on
 * the Cast SDK feature.
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
