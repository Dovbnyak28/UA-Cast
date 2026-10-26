package com.uacastplayer.epg

data class CurrentNextProgrammes(
    val current: EpgProgramme?,
    val next: EpgProgramme?,
    val effectiveStopMillis: Long?,
)

/**
 * Finds the currently-airing and next programme for a channel via binary search. The "current"
 * programme's effective end is always the next programme's start, not its own declared stop time
 * - feeds routinely have small gaps/overlaps between declared stop and the next start, and the
 * start times are the more reliable signal.
 */
object ProgrammeLookup {

    /** [programmes] must already be sorted ascending by [EpgProgramme.startMillis]. */
    fun currentAndNext(programmes: List<EpgProgramme>, nowMillis: Long): CurrentNextProgrammes {
        if (programmes.isEmpty()) return EMPTY_RESULT
        val currentIndex = findCurrentIndex(programmes, nowMillis)
        return resultForIndex(programmes, currentIndex, nowMillis)
    }

    private fun findCurrentIndex(programmes: List<EpgProgramme>, nowMillis: Long): Int {
        var low = 0
        var high = programmes.size - 1
        var currentIndex = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (programmes[mid].startMillis <= nowMillis) {
                currentIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return currentIndex
    }

    private fun resultForIndex(
        programmes: List<EpgProgramme>,
        currentIndex: Int,
        nowMillis: Long,
    ): CurrentNextProgrammes = when {
        currentIndex == -1 -> {
            val first = programmes[0]
            CurrentNextProgrammes(current = null, next = first, effectiveStopMillis = first.startMillis)
        }
        isLastProgrammeFinished(programmes[currentIndex], programmes.getOrNull(currentIndex + 1), nowMillis) -> {
            EMPTY_RESULT
        }
        else -> {
            val current = programmes[currentIndex]
            val next = programmes.getOrNull(currentIndex + 1)
            CurrentNextProgrammes(current, next, next?.startMillis ?: current.stopMillis)
        }
    }

    private fun isLastProgrammeFinished(
        current: EpgProgramme,
        next: EpgProgramme?,
        nowMillis: Long,
    ): Boolean = next == null &&
        current.stopMillis > current.startMillis &&
        nowMillis >= current.stopMillis

    private val EMPTY_RESULT = CurrentNextProgrammes(null, null, null)
}
