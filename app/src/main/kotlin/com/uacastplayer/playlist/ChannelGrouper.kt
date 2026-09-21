package com.uacastplayer.playlist

import java.text.Collator
import java.util.Locale

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

    /**
     * @param locale whose alphabet orders the custom groups - see [FavoritesSorter] for the same
     *   correction and the measurement behind it. This is the list a viewer scrolls to find a
     *   folder, so having it in an order their alphabet does not recognise is the version of this
     *   bug that costs the most.
     */
    fun group(
        channels: List<M3uChannel>,
        locale: Locale = Locale.getDefault(),
        checkCancellation: () -> Unit = {},
    ): List<GroupedChannels> {
        val byGroup = linkedMapOf<ChannelGroup, MutableList<M3uChannel>>()
        checkCancellation()
        for ((index, channel) in channels.withIndex()) {
            if (index % CANCELLATION_CHECK_INTERVAL_CHANNELS == 0) checkCancellation()
            val group = ChannelGroupNormalizer.normalize(channel.groupTitle)
            byGroup.getOrPut(group) { mutableListOf() }.add(channel)
        }

        checkCancellation()
        val known = byGroup.keys.filterIsInstance<ChannelGroup.Known>()
            .sortedBy { knownOrder.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        // A Collator, not `lowercase()`: lowercasing sorts by UTF-16 code unit, which puts Ukrainian
        // Ґ after я and Є/І/Ї in a block of their own past the end of the alphabet.
        val custom = byGroup.keys.filterIsInstance<ChannelGroup.Custom>()
            .sortedWith(compareBy(Collator.getInstance(locale)) { it.rawTitle })
        val ungrouped = byGroup.keys.filterIsInstance<ChannelGroup.Ungrouped>()

        checkCancellation()
        return (known + custom + ungrouped).map { GroupedChannels(it, byGroup.getValue(it)) }
    }

    private const val CANCELLATION_CHECK_INTERVAL_CHANNELS = 256
}
