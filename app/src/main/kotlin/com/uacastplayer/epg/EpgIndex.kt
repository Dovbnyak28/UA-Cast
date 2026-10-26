package com.uacastplayer.epg

import com.uacastplayer.playlist.M3uChannel

/**
 * Resolves an M3U channel to its XMLTV [EpgChannel], trying progressively fuzzier signals:
 * exact tvg-id, then normalized tvg-id, then normalized tvg-name, then normalized display name.
 */
class EpgIndex(val channels: List<EpgChannel>, checkCancellation: () -> Unit = {}) {

    private val epgChannels = channels

    private val byExactId: Map<String, EpgChannel> = epgChannels.associateBy { checkCancellation(); it.id }
    private val byNormalizedId: Map<String, EpgChannel> =
        epgChannels.associateBy { checkCancellation(); EpgChannelNameNormalizer.normalize(it.id) }
    private val byNormalizedName: Map<String, EpgChannel> = buildMap {
        for (channel in epgChannels) {
            checkCancellation()
            for (name in channel.displayNames) {
                putIfAbsent(EpgChannelNameNormalizer.normalize(name), channel)
            }
        }
    }

    fun match(channel: M3uChannel): EpgChannel? {
        return channel.tvgId?.let(byExactId::get)
            ?: channel.tvgId?.let(EpgChannelNameNormalizer::normalize)?.let(byNormalizedId::get)
            ?: channel.tvgName?.let(EpgChannelNameNormalizer::normalize)?.let(byNormalizedName::get)
            ?: byNormalizedName[EpgChannelNameNormalizer.normalize(channel.displayName)]
    }
}
