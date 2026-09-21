package com.uacastplayer.core.cast

sealed interface CastCompatibilityVerdict {
    data object Compatible : CastCompatibilityVerdict

    /** Neither codec is confirmed to fail, but at least one isn't the safe default (H.264 video +
     * AAC audio) either - [audioHint]/[videoHint] carry which one, purely so a post-failure message
     * can name a likely cause. Casting proceeds exactly like [Compatible]: this never blocks or
     * skips an attempt (see `CastDeliveryStrategy.onDiagnosticResult`) - real receivers routinely
     * play HEVC and MP2/AC-3/E-AC-3 audio despite Chromecast's Default Receiver not officially
     * guaranteeing it, so treating either as a hard failure would block channels that actually work. */
    data class LikelyCompatible(val audioHint: AudioCodec?, val videoHint: VideoCodec?) : CastCompatibilityVerdict

    /** The one confirmed, hard incompatibility: MPEG-2 video is essentially never hardware-decoded
     * by real Cast receivers, and remuxing the container can't fix a codec problem (see
     * [com.uacastplayer.proxy.RawTsRemuxActivation]) - so this is the only verdict that blocks a
     * cast attempt outright rather than just informing a later failure message. */
    data class IncompatibleVideo(val codec: VideoCodec) : CastCompatibilityVerdict
    data object Unknown : CastCompatibilityVerdict
}

/**
 * Chromecast's Default Receiver plays H.264 video with AAC audio without question; everything else
 * - HEVC video, MP2/AC-3/E-AC-3 audio - is a coin flip that depends on the actual receiver hardware,
 * not a confirmed failure, so it must never stop a cast attempt from being tried (see
 * [CastCompatibilityVerdict.LikelyCompatible]). Only MPEG-2 video is blocked outright: it is about
 * as close to "never works" as a pre-flight check can be confident of. [info] being null (PAT/PMT
 * not found/parseable) is [CastCompatibilityVerdict.Unknown] - the diagnostic simply couldn't tell,
 * so callers fall back to their existing "wait and see" (watchdog) behavior.
 */
object CastCompatibilityPolicy {

    fun classify(info: TsProgramInfo?): CastCompatibilityVerdict {
        if (info == null) return CastCompatibilityVerdict.Unknown
        return classifyKnown(info)
    }

    private fun classifyKnown(info: TsProgramInfo): CastCompatibilityVerdict {
        val videoCodec = info.videoCodec
        if (videoCodec == VideoCodec.Mpeg2Video) return CastCompatibilityVerdict.IncompatibleVideo(videoCodec)

        val videoHint = videoCodec?.takeIf { it != VideoCodec.H264 }
        // "AAC-less audio" - a track list with at least one AAC track is fine even if other
        // language/format tracks aren't, since the receiver only ever plays one at a time.
        val audioHint = info.audioCodecs
            .takeIf { it.isNotEmpty() && it.none { codec -> codec == AudioCodec.Aac || codec == AudioCodec.AacLatm } }
            ?.first()

        return when {
            videoHint != null || audioHint != null -> CastCompatibilityVerdict.LikelyCompatible(audioHint, videoHint)
            videoCodec == null && info.audioCodecs.isEmpty() -> CastCompatibilityVerdict.Unknown
            else -> CastCompatibilityVerdict.Compatible
        }
    }
}
