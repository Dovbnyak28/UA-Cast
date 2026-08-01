package com.uacastplayer.epg

data class EpgChannel(
    val id: String,
    val displayNames: List<String>,
    val iconUrl: String?,
)

/**
 * A programme carries no description on purpose. XMLTV's `<desc>` is by far the largest field in a
 * real feed - measured at 37M characters (~74MB retained as UTF-16 Cyrillic) for a 500-channel,
 * 200k-programme Ukrainian feed, about two thirds of the entire parsed EPG heap - and nothing in
 * the app ever read it: the guide sheet renders a title and a time and stops there. Holding it
 * pinned the heap at the 256MB growth limit and the process died of an OutOfMemoryError mid-parse.
 *
 * If descriptions are ever shown, they must not come back as a field on 250k retained rows. Read
 * them on demand from the cached document instead (see EpgSnapshotStore, which already keeps the
 * raw XML) so only the handful currently on screen are in memory.
 */
data class EpgProgramme(
    val channelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
)
