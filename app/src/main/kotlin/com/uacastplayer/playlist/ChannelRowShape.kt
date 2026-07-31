package com.uacastplayer.playlist

/**
 * Which corners a per-row card should round when a channel list is rendered as separate LazyColumn
 * items (see SingleGroupChannelList) instead of one big Column inside a single item, so the list
 * still reads as one continuous rounded card: only the first row needs top corners, only the last
 * needs bottom corners, and everything between stays square so there's no visible seam. A
 * single-item list is both first and last, so it gets all four corners.
 */
object ChannelRowShape {
    data class Rounding(val top: Boolean, val bottom: Boolean)

    fun roundingFor(index: Int, lastIndex: Int): Rounding =
        Rounding(top = index == 0, bottom = index == lastIndex)
}
