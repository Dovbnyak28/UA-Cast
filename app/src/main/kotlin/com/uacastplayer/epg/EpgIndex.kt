package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/**
 * Resolves an M3U channel to its XMLTV [EpgChannel], trying progressively fuzzier signals:
 * exact tvg-id, then normalized tvg-id, then normalized tvg-name, then normalized display name.
 */
class EpgIndex(val channels: List<EpgChannel>) {

    private val epgChannels = channels

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
        val exactIdMatch = channel.tvgId?.let(byExactId::get)
        val normalizedIdMatch = channel.tvgId
            ?.let(EpgChannelNameNormalizer::normalize)
            ?.let(byNormalizedId::get)
        val normalizedNameMatch = channel.tvgName
            ?.let(EpgChannelNameNormalizer::normalize)
            ?.let(byNormalizedName::get)
        return exactIdMatch
            ?: normalizedIdMatch
            ?: normalizedNameMatch
            ?: byNormalizedName[EpgChannelNameNormalizer.normalize(channel.displayName)]
    }
}
