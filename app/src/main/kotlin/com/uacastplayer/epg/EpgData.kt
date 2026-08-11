package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/** Parsed, ready-to-query EPG state: a channel-matching index plus programmes sorted per channel. */
data class EpgData(
    val index: EpgIndex,
    val programmesByChannelId: Map<String, List<EpgProgramme>>,
    /** Whether the feed was larger than [XmlTvParser]'s caps and had to be cut short. */
    val truncation: EpgTruncation = EpgTruncation.NONE,
)

/**
 * Records that a feed exceeded [XmlTvParser.MAX_CHANNELS]/[XmlTvParser.MAX_PROGRAMMES] and was cut
 * off at the cap.
 *
 * The parser has always computed this, and until now absolutely nothing read it - the flags went
 * into `XmlTvParseResult` and died there, with the only references outside the parser being two
 * `assertFalse`s in its own test. That silence is the problem: XMLTV is normally ordered by
 * channel, so hitting the cap does not thin the guide out evenly, it leaves the *last* channels in
 * the file with no programmes at all. A real 500-channel Ukrainian feed hits `MAX_PROGRAMMES`
 * exactly, and the app said nothing about it anywhere - not in the UI, not even in a log line.
 *
 * Since [EpgRetentionPolicy] began dropping finished programmes as they stream past, this should be
 * rare rather than routine: the shipped feed went from 793,417 programmes (two thirds of them cut)
 * to 346,837, comfortably inside the cap. The flag stays because a feed can still be larger than
 * any number chosen here, and the day one is, the user deserves to be told which part is missing.
 */
data class EpgTruncation(
    val channelsDropped: Boolean,
    val programmesDropped: Boolean,
) {
    val any: Boolean get() = channelsDropped || programmesDropped

    companion object {
        val NONE = EpgTruncation(channelsDropped = false, programmesDropped = false)
    }
}

/** Looks up the current/next programme for an M3U channel, resolving it against the EPG index first. */
object EpgLookup {
    fun currentAndNext(data: EpgData, channel: M3uChannel, nowMillis: Long): CurrentNextProgrammes? {
        val epgChannel = data.index.match(channel) ?: return null
        val programmes = data.programmesByChannelId[epgChannel.id] ?: return null
        return ProgrammeLookup.currentAndNext(programmes, nowMillis)
    }
}
