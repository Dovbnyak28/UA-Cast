package com.uacastplayer.proxy

/** MPEG-2 video and MP2 audio are common on IPTV feeds but essentially never hardware-decoded by Cast receivers. */
object TsCompatibilityPolicy {
    fun isKnownUnsupported(info: TsStreamInfo): Boolean =
        info.videoCodec == TsCodec.MPEG2_VIDEO || info.audioCodec == TsCodec.MPEG_AUDIO
}
