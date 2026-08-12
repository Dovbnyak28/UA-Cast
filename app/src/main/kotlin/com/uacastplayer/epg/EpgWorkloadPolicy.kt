package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/**
 * How much guide this viewer actually has - the programmes belonging to channels that are in their
 * playlist, not every programme in the feed.
 *
 * [com.uacastplayer.performance.DevicePerformanceClassifier.adjustForContentSize] is the only
 * caller, and the distinction is the whole point of this file. It was being handed the feed's total,
 * and a feed is not a viewer's guide: a diagnostics report from the field carried a playlist of 311
 * channels against a guide of 4052, so 92% of the number deciding that device's performance tier was
 * channels its owner did not have. The tier landed on LOW_END on hardware that classifies MID_RANGE,
 * which turns channel logos off entirely ([com.uacastplayer.performance.DeviceTierDefaults]) - and
 * the two devices that have sent reports both show `icon_display_mode` written to `CACHE_LIMITED`,
 * which is what the "enable icons" banner writes. Both owners were shown a wall of placeholders and
 * both turned them back on by hand.
 *
 * The thresholds it feeds were always written for this number rather than the feed's: 300 channels
 * over the retained window is roughly 18,000 programmes, comfortably inside the smallest band, which
 * is the behaviour a 300-channel playlist should get.
 *
 * Matching is [EpgIndex.match], the same resolution the channel rows and the guide sheet use, so
 * this counts a channel as having a guide exactly when the user can see one.
 *
 * Cost, measured against the three real playlists and the real guide on the test device rather than
 * assumed: **4ms, 2ms and 1ms** for playlists of 2863, 1543 and 1309 channels, once per load. Worth
 * stating how those were reached, because the cheap path is not the one that runs: not one of those
 * 2863 channels carries a `tvg-id`, so `match`'s first two attempts miss every time and the answer
 * comes from the normalised-name map on the third. Real playlists in this app are matched by name,
 * and it is still only milliseconds.
 */
object EpgWorkloadPolicy {

    /**
     * Programmes held for the channels in [channels]; 0 when there is no guide yet.
     *
     * Distinct EPG channels are counted once even when several playlist entries resolve to the same
     * one, which is routine: providers list `Channel HD`, `Channel FHD` and `Channel SD` separately
     * and all three carry one `tvg-id`. Counting per playlist row would have multiplied the guide by
     * the playlist's own duplication and put a small playlist over the threshold for having three
     * quality variants of everything.
     */
    fun programmesFor(data: EpgData?, channels: List<M3uChannel>): Int {
        if (data == null) return 0
        val matched = HashSet<String>()
        for (channel in channels) {
            data.index.match(channel)?.let { matched.add(it.id) }
        }
        return matched.sumOf { id -> data.programmesByChannelId[id]?.size ?: 0 }
    }
}
