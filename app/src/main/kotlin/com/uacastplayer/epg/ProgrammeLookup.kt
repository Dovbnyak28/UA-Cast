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
        if (programmes.isEmpty()) return CurrentNextProgrammes(null, null, null)

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

        if (currentIndex == -1) {
            val first = programmes[0]
            return CurrentNextProgrammes(current = null, next = first, effectiveStopMillis = first.startMillis)
        }

        val current = programmes[currentIndex]
        val next = programmes.getOrNull(currentIndex + 1)
        val effectiveStop = next?.startMillis ?: current.stopMillis
        return CurrentNextProgrammes(current, next, effectiveStop)
    }
}
