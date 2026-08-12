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
        // Past the end of a channel's listings there is no current programme - the mirror of the
        // "before the first" case above, which was handled while this was not. The search always
        // returns the last programme once `now` is past it, and nothing later ever changes that
        // answer, so the row kept showing a finished programme with a live dot and a full progress
        // bar. Only reachable for the LAST programme: any other one ends when its successor starts.
        //
        // `stopMillis > startMillis` guards the feeds that give no stop time at all, where
        // EpgProgramme falls back to the start (see its KDoc). Zero declared length is not evidence
        // that a programme is over, and reading it that way would blank the badge on such a feed
        // for every channel at once.
        if (next == null && current.stopMillis > current.startMillis && nowMillis >= current.stopMillis) {
            return CurrentNextProgrammes(current = null, next = null, effectiveStopMillis = null)
        }
        val effectiveStop = next?.startMillis ?: current.stopMillis
        return CurrentNextProgrammes(current, next, effectiveStop)
    }
}
