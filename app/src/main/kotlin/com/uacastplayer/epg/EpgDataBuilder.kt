package com.uacastplayer.epg

import java.util.LinkedHashMap

/**
 * Builds the query-ready guide from the SAX parser's flat result.
 *
 * Kept separate from `data.epg.EpgRepository`: grouping, sorting and index construction are pure
 * EPG-domain work, while the repository owns download/cache lifecycle. The explicit cancellation
 * callback keeps long builds cooperative without coupling this policy to coroutines.
 */
object EpgDataBuilder {

    private const val CANCELLATION_CHECK_INTERVAL = 256
    private const val DEFAULT_MAP_CAPACITY = 16
    private const val MAX_MAP_CAPACITY = 1 shl 30

    fun build(parsed: XmlTvParseResult, checkCancellation: () -> Unit = {}): EpgData {
        // Most programmes belong to a declared channel. Sizing from the smaller input avoids
        // repeated LinkedHashMap table growth on large guides while retaining a useful default
        // for feeds whose programmes reference undeclared channels.
        val expectedEntries = minOf(parsed.channels.size, parsed.programmes.size)
        val initialCapacity = initialMapCapacity(expectedEntries)
        val mutableProgrammesByChannel =
            LinkedHashMap<String, MutableList<EpgProgramme>>(initialCapacity)
        for ((index, programme) in parsed.programmes.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0) checkCancellation()
            mutableProgrammesByChannel.getOrPut(programme.channelId) { mutableListOf() }.add(programme)
        }
        for (programmes in mutableProgrammesByChannel.values) {
            checkCancellation()
            programmes.sortBy { it.startMillis }
        }
        val truncation = EpgTruncation(
            // Persist the existing incomplete-channel-metadata warning without changing cache format.
            channelsDropped = parsed.channelLimitExceeded || parsed.aliasLimitExceeded,
            programmesDropped = parsed.programmeLimitExceeded,
        )
        checkCancellation()
        return EpgData(
            index = EpgIndex(parsed.channels, checkCancellation),
            programmesByChannelId = mutableProgrammesByChannel,
            truncation = truncation,
        )
    }

    private fun initialMapCapacity(expectedEntries: Int): Int {
        if (expectedEntries < DEFAULT_MAP_CAPACITY) return DEFAULT_MAP_CAPACITY

        // HashMap uses a 0.75 load factor; account for it before allocating, with overflow capped
        // at the largest supported table size.
        val capacity = (expectedEntries.toLong() * 4L + 2L) / 3L
        return capacity.coerceAtMost(MAX_MAP_CAPACITY.toLong()).toInt()
    }
}
