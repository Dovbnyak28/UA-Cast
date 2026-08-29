package com.uacastplayer.epg

/**
 * Builds the query-ready guide from the SAX parser's flat result.
 *
 * Kept separate from `data.epg.EpgRepository`: grouping, sorting and index construction are pure
 * EPG-domain work, while the repository owns download/cache lifecycle. The explicit cancellation
 * callback keeps long builds cooperative without coupling this policy to coroutines.
 */
object EpgDataBuilder {

    private const val CANCELLATION_CHECK_INTERVAL = 256

    fun build(parsed: XmlTvParseResult, checkCancellation: () -> Unit = {}): EpgData {
        val mutableProgrammesByChannel = linkedMapOf<String, MutableList<EpgProgramme>>()
        for ((index, programme) in parsed.programmes.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0) checkCancellation()
            mutableProgrammesByChannel.getOrPut(programme.channelId) { mutableListOf() }.add(programme)
        }
        for (programmes in mutableProgrammesByChannel.values) {
            checkCancellation()
            programmes.sortBy { it.startMillis }
        }
        val truncation = EpgTruncation(
            channelsDropped = parsed.channelLimitExceeded,
            programmesDropped = parsed.programmeLimitExceeded,
        )
        checkCancellation()
        return EpgData(
            index = EpgIndex(parsed.channels),
            programmesByChannelId = mutableProgrammesByChannel,
            truncation = truncation,
        )
    }
}
