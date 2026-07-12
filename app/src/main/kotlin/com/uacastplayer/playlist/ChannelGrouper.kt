package com.uacastplayer.playlist

data class GroupedChannels(val group: ChannelGroup, val channels: List<M3uChannel>)

/**
 * Buckets channels by their normalized [ChannelGroup] and orders the buckets for display: known
 * categories first (in a fixed, curated order), then custom provider groups alphabetically, with
 * ungrouped channels always last.
 */
object ChannelGrouper {

    private val knownOrder = listOf(
        ChannelGroup.KEY_NEWS,
        ChannelGroup.KEY_MOVIES,
        ChannelGroup.KEY_SERIES,
        ChannelGroup.KEY_ENTERTAINMENT,
        ChannelGroup.KEY_KIDS,
        ChannelGroup.KEY_SPORTS,
        ChannelGroup.KEY_MUSIC,
        ChannelGroup.KEY_DOCUMENTARY,
        ChannelGroup.KEY_SCIENCE,
        ChannelGroup.KEY_RELIGION,
        ChannelGroup.KEY_REGIONAL,
    )

    fun group(channels: List<M3uChannel>): List<GroupedChannels> {
        val byGroup = linkedMapOf<ChannelGroup, MutableList<M3uChannel>>()
        for (channel in channels) {
            val group = ChannelGroupNormalizer.normalize(channel.groupTitle)
            byGroup.getOrPut(group) { mutableListOf() }.add(channel)
        }

        val known = byGroup.keys.filterIsInstance<ChannelGroup.Known>()
            .sortedBy { knownOrder.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        val custom = byGroup.keys.filterIsInstance<ChannelGroup.Custom>()
            .sortedBy { it.rawTitle.lowercase() }
        val ungrouped = byGroup.keys.filterIsInstance<ChannelGroup.Ungrouped>()

        return (known + custom + ungrouped).map { GroupedChannels(it, byGroup.getValue(it)) }
    }
}
