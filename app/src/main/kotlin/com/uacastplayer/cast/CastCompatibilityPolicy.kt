package com.uacastplayer.cast

sealed class CastCompatibilityVerdict {
    data object Compatible : CastCompatibilityVerdict()
    data class IncompatibleAudio(val codec: AudioCodec) : CastCompatibilityVerdict()
    data class IncompatibleVideo(val codec: VideoCodec) : CastCompatibilityVerdict()
    data object Unknown : CastCompatibilityVerdict()
}

/**
 * Chromecast's Default Receiver plays H.264 (+VP9/AV1 on newer hardware) video with AAC audio;
 * MPEG-2/HEVC video and MP2/AC-3/E-AC-3-only audio are essentially never hardware-decoded by real
 * receivers. Video takes priority when both are bad, since a video codec mismatch is the more
 * fundamental failure. [info] being null (PAT/PMT not found/parseable) is [Unknown], not a hard
 * incompatibility - the diagnostic simply couldn't tell, so callers should fall back to their
 * existing "wait and see" (watchdog) behavior rather than surfacing a false compatibility warning.
 */
object CastCompatibilityPolicy {

    fun classify(info: TsProgramInfo?): CastCompatibilityVerdict {
        if (info == null) return CastCompatibilityVerdict.Unknown
        val videoCodec = info.videoCodec

        // "MP2/AC3/EAC3-only audio" - a track list with at least one AAC track is fine even if
        // other language/format tracks aren't, since the receiver only ever plays one at a time.
        val incompatibleAudioCodec = info.audioCodecs
            .takeIf { it.isNotEmpty() && it.none { codec -> codec == AudioCodec.Aac || codec == AudioCodec.AacLatm } }
            ?.first()

        return when {
            videoCodec != null && videoCodec != VideoCodec.H264 -> CastCompatibilityVerdict.IncompatibleVideo(videoCodec)
            incompatibleAudioCodec != null -> CastCompatibilityVerdict.IncompatibleAudio(incompatibleAudioCodec)
            videoCodec == null && info.audioCodecs.isEmpty() -> CastCompatibilityVerdict.Unknown
            else -> CastCompatibilityVerdict.Compatible
        }
    }
}
