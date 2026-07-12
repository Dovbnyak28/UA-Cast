package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/** Parsed, ready-to-query EPG state: a channel-matching index plus programmes sorted per channel. */
data class EpgData(
    val index: EpgIndex,
    val programmesByChannelId: Map<String, List<EpgProgramme>>,
)

/** Looks up the current/next programme for an M3U channel, resolving it against the EPG index first. */
object EpgLookup {
    fun currentAndNext(data: EpgData, channel: M3uChannel, nowMillis: Long): CurrentNextProgrammes? {
        val epgChannel = data.index.match(channel) ?: return null
        val programmes = data.programmesByChannelId[epgChannel.id] ?: return null
        return ProgrammeLookup.currentAndNext(programmes, nowMillis)
    }
}
