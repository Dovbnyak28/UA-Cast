package com.uacastplayer.epg

import java.time.ZoneId

/**
 * When a cached guide has stopped being this week's guide.
 *
 * The snapshot had no expiry at all: `EpgController.loadInitial` downloaded only when there was no
 * snapshot, and after that nothing in the app ever fetched the guide again on its own - not on a
 * timer, not on a schedule, not on age. `EpgSnapshotHeader.savedAtEpochMillis` was written into
 * every snapshot and read back by the codec, and no logic anywhere consulted it. A device that
 * downloaded the guide once kept exactly that guide until the feed's window ran out, at which point
 * the sheet went empty and the now/next badges vanished - which a user reports as "the TV guide
 * stopped working", with nothing to do about it but re-pick the source in Settings.
 *
 * Staleness is the same calendar-day boundary [EpgRetentionPolicy] cuts on, and deliberately so: a
 * snapshot saved before today began was built with an older cutoff, so it is already carrying a day
 * it would now discard and covering a day less of the future than a fresh one would.
 *
 * The metering condition is not an optimisation. The feed is ~50MB compressed, and spending that
 * every day on somebody's mobile data - for a guide they did not ask to refresh - is not a cost this
 * app gets to impose. On a metered network the cached guide is kept and nothing is downloaded; the
 * next launch that has Wi-Fi refreshes it.
 */
object EpgRefreshPolicy {

    /** Whether [savedAtMillis] predates the start of the day containing [nowMillis]. */
    fun isStale(savedAtMillis: Long, nowMillis: Long, zoneId: ZoneId): Boolean =
        savedAtMillis < EpgRetentionPolicy.keepFrom(nowMillis, zoneId)

    fun shouldRefresh(savedAtMillis: Long, nowMillis: Long, zoneId: ZoneId, isUnmetered: Boolean): Boolean =
        isUnmetered && isStale(savedAtMillis, nowMillis, zoneId)
}
