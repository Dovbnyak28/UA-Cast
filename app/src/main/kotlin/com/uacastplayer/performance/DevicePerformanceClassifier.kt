package com.uacastplayer.performance

/**
 * Scores a device from RAM/cores/SDK level (0-6, two points each for a "good", one for "ok"),
 * then lets [adjustForContentSize] knock the result down by up to two tiers when the currently
 * loaded playlist and guide are large enough that even solid hardware will feel it.
 *
 * "Up to two" is what the code has always done, and the sentence here used to say "a notch". On a
 * three-tier scale two notches from anywhere is the floor, so it is worth being exact: content
 * alone can make the hardware score irrelevant.
 */
object DevicePerformanceClassifier {

    private const val BYTES_PER_GIBIBYTE = 1024L * 1024L * 1024L
    private const val HIGH_RAM_GIB = 4
    private const val MID_RAM_GIB = 2
    private const val HIGH_CPU_CORE_COUNT = 8
    private const val MID_CPU_CORE_COUNT = 4
    private const val HIGH_SDK_INT = 31
    private const val MID_SDK_INT = 26
    private const val HIGH_SCORE_INCREMENT = 2
    private const val MID_SCORE_INCREMENT = 1
    private const val LARGE_PLAYLIST_CHANNEL_COUNT = 5_000
    private const val MEDIUM_PLAYLIST_CHANNEL_COUNT = 1_500
    private const val LARGE_EPG_PROGRAMME_COUNT = 100_000
    private const val MEDIUM_EPG_PROGRAMME_COUNT = 30_000
    private const val LARGE_CONTENT_PENALTY = 2
    private const val MEDIUM_CONTENT_PENALTY = 1
    private const val HIGH_END_MIN_SCORE = 5
    private const val MID_RANGE_MIN_SCORE = 2

    fun classify(totalRamBytes: Long, cpuCoreCount: Int, sdkInt: Int): DeviceTier {
        val ramGb = totalRamBytes.toDouble() / BYTES_PER_GIBIBYTE
        var score = 0
        score += when {
            ramGb >= HIGH_RAM_GIB -> HIGH_SCORE_INCREMENT
            ramGb >= MID_RAM_GIB -> MID_SCORE_INCREMENT
            else -> 0
        }
        score += when {
            cpuCoreCount >= HIGH_CPU_CORE_COUNT -> HIGH_SCORE_INCREMENT
            cpuCoreCount >= MID_CPU_CORE_COUNT -> MID_SCORE_INCREMENT
            else -> 0
        }
        score += when {
            sdkInt >= HIGH_SDK_INT -> HIGH_SCORE_INCREMENT
            sdkInt >= MID_SDK_INT -> MID_SCORE_INCREMENT
            else -> 0
        }
        return tierForScore(score)
    }

    /**
     * @param epgProgrammeCount programmes held **for channels in this playlist**, which is what
     *   [com.uacastplayer.epg.EpgWorkloadPolicy] computes - not the feed's total.
     *
     *   The feed's total is what was passed here for a long time, and the two are not close. A
     *   report from the field carried a playlist of 311 channels against a guide of 4052: the
     *   number deciding that device's tier was 92% channels its owner did not have, it cleared the
     *   heavier threshold on its own, and the phone was classified LOW_END on hardware that scores
     *   MID_RANGE. LOW_END turns channel logos off outright (see [DeviceTierDefaults]), and both
     *   devices that have sent reports show the icon mode written to the value the "enable icons"
     *   banner writes - two owners, both shown placeholders, both undoing it by hand.
     *
     *   The thresholds below were always sized for this number. Three hundred channels over the
     *   retained window is roughly 18,000 programmes, which is inside the smallest band - the
     *   behaviour a 300-channel playlist should get.
     */
    fun adjustForContentSize(tier: DeviceTier, playlistChannelCount: Int, epgProgrammeCount: Int): DeviceTier {
        val penalty = when {
            playlistChannelCount > LARGE_PLAYLIST_CHANNEL_COUNT ||
                epgProgrammeCount > LARGE_EPG_PROGRAMME_COUNT -> LARGE_CONTENT_PENALTY
            playlistChannelCount > MEDIUM_PLAYLIST_CHANNEL_COUNT ||
                epgProgrammeCount > MEDIUM_EPG_PROGRAMME_COUNT -> MEDIUM_CONTENT_PENALTY
            else -> 0
        }
        if (penalty == 0) return tier
        val downgraded = DeviceTier.entries.indexOf(tier) - penalty
        return DeviceTier.entries[downgraded.coerceAtLeast(0)]
    }

    private fun tierForScore(score: Int): DeviceTier = when {
        score >= HIGH_END_MIN_SCORE -> DeviceTier.HIGH_END
        score >= MID_RANGE_MIN_SCORE -> DeviceTier.MID_RANGE
        else -> DeviceTier.LOW_END
    }
}
