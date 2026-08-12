package com.uacastplayer.epg

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expiry the cached guide never had. Every snapshot carried the day it was written and no code
 * anywhere asked - so a device that downloaded the guide once kept it until the feed's window ran
 * out and the sheet went empty.
 */
class EpgRefreshPolicyTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, kyiv).toInstant().toEpochMilli()

    @Test
    fun aGuideSavedYesterdayIsStale() {
        assertTrue(EpgRefreshPolicy.isStale(at(day = 10, hour = 23), at(day = 11, hour = 9), kyiv))
    }

    @Test
    fun aGuideSavedThisMorningIsNot() {
        assertFalse(EpgRefreshPolicy.isStale(at(day = 11, hour = 7), at(day = 11, hour = 21), kyiv))
    }

    /** The boundary is the same midnight [EpgRetentionPolicy] cuts on, so the two cannot disagree
     * about which day a snapshot belongs to. */
    @Test
    fun theBoundaryIsMidnightNotTwentyFourHoursAgo() {
        assertFalse(
            "a guide saved at one minute past midnight is today's",
            EpgRefreshPolicy.isStale(at(day = 11, hour = 0, minute = 1), at(day = 11, hour = 23), kyiv),
        )
        assertTrue(
            "a guide saved one minute before midnight is yesterday's",
            EpgRefreshPolicy.isStale(at(day = 10, hour = 23, minute = 59), at(day = 11, hour = 0, minute = 5), kyiv),
        )
    }

    /**
     * ~50MB of somebody's mobile data, for a refresh they did not ask for. The cached guide is kept
     * and the next launch on Wi-Fi picks it up instead.
     */
    @Test
    fun aStaleGuideIsNotRefreshedOverMeteredData() {
        assertFalse(
            EpgRefreshPolicy.shouldRefresh(
                savedAtMillis = at(day = 1, hour = 12),
                nowMillis = at(day = 11, hour = 12),
                zoneId = kyiv,
                isUnmetered = false,
            ),
        )
    }

    @Test
    fun aStaleGuideOnWifiIsRefreshed() {
        assertTrue(
            EpgRefreshPolicy.shouldRefresh(
                savedAtMillis = at(day = 10, hour = 12),
                nowMillis = at(day = 11, hour = 12),
                zoneId = kyiv,
                isUnmetered = true,
            ),
        )
    }

    /** Wi-Fi is permission to spend data, not a reason to. A guide from this morning is current. */
    @Test
    fun aCurrentGuideIsNotRedownloadedJustBecauseWifiIsAvailable() {
        assertFalse(
            EpgRefreshPolicy.shouldRefresh(
                savedAtMillis = at(day = 11, hour = 8),
                nowMillis = at(day = 11, hour = 12),
                zoneId = kyiv,
                isUnmetered = true,
            ),
        )
    }
}
