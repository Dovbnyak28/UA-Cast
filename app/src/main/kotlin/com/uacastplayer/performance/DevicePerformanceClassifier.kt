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

    fun classify(totalRamBytes: Long, cpuCoreCount: Int, sdkInt: Int): DeviceTier {
        val ramGb = totalRamBytes.toDouble() / (1024 * 1024 * 1024)
        var score = 0
        score += when {
            ramGb >= 4 -> 2
            ramGb >= 2 -> 1
            else -> 0
        }
        score += when {
            cpuCoreCount >= 8 -> 2
            cpuCoreCount >= 4 -> 1
            else -> 0
        }
        score += when {
            sdkInt >= 31 -> 2
            sdkInt >= 26 -> 1
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
            playlistChannelCount > 5000 || epgProgrammeCount > 100_000 -> 2
            playlistChannelCount > 1500 || epgProgrammeCount > 30_000 -> 1
            else -> 0
        }
        if (penalty == 0) return tier
        val downgraded = DeviceTier.entries.indexOf(tier) - penalty
        return DeviceTier.entries[downgraded.coerceAtLeast(0)]
    }

    private fun tierForScore(score: Int): DeviceTier = when {
        score >= 5 -> DeviceTier.HIGH_END
        score >= 2 -> DeviceTier.MID_RANGE
        else -> DeviceTier.LOW_END
    }
}
