package com.uacastplayer.icons

import com.uacastplayer.playlist.M3uChannel

/**
 * Which channels ui/components/GroupIconCollage.kt tries to show a logo for on a group card - up
 * to [MAX_TILES], in the group's own channel order (so the collage is deterministic and doesn't
 * jump around as the group's channel list changes elsewhere), limited to channels that have *some*
 * icon candidate at all (a bare tvgLogo or tvgId - see [IconResolver.candidates]). Whether a given
 * candidate actually has a cached file is resolved separately, per candidate, at render time; this
 * only decides who's worth trying so the collage isn't wasting a tile slot on a channel that could
 * never have shown anything.
 */
object GroupCollagePolicy {
    const val MAX_TILES = 4

    fun candidateChannels(channels: List<M3uChannel>): List<M3uChannel> =
        channels.filter(::hasIconCandidate).take(MAX_TILES)

    private fun hasIconCandidate(channel: M3uChannel): Boolean =
        !channel.tvgLogo.isNullOrBlank() || !channel.tvgId.isNullOrBlank()
}
