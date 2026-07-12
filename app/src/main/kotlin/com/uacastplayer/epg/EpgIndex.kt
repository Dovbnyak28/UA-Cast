package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/**
 * Resolves an M3U channel to its XMLTV [EpgChannel], trying progressively fuzzier signals:
 * exact tvg-id, then normalized tvg-id, then normalized tvg-name, then normalized display name.
 */
class EpgIndex(epgChannels: List<EpgChannel>) {

    private val byExactId: Map<String, EpgChannel> = epgChannels.associateBy { it.id }
    private val byNormalizedId: Map<String, EpgChannel> =
        epgChannels.associateBy { EpgChannelNameNormalizer.normalize(it.id) }
    private val byNormalizedName: Map<String, EpgChannel> = buildMap {
        for (channel in epgChannels) {
            for (name in channel.displayNames) {
                putIfAbsent(EpgChannelNameNormalizer.normalize(name), channel)
            }
        }
    }

    fun match(channel: M3uChannel): EpgChannel? {
        channel.tvgId?.let { id -> byExactId[id]?.let { return it } }
        channel.tvgId?.let { id -> byNormalizedId[EpgChannelNameNormalizer.normalize(id)]?.let { return it } }
        channel.tvgName?.let { name -> byNormalizedName[EpgChannelNameNormalizer.normalize(name)]?.let { return it } }
        return byNormalizedName[EpgChannelNameNormalizer.normalize(channel.displayName)]
    }
}
