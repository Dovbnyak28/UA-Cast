package com.uacastplayer.performance

/**
 * Scores a device from RAM/cores/SDK level (0-6, two points each for a "good", one for "ok"),
 * then lets [adjustForContentSize] knock the result down a notch when the currently loaded
 * playlist/EPG is large enough that even solid hardware will feel it.
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
