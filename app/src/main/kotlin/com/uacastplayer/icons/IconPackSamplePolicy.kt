package com.uacastplayer.icons

import com.uacastplayer.playlist.M3uChannel

data class IconPackSamplePlan(val channels: List<M3uChannel>, val missingIds: Int, val total: Int)

/** Scan once, deduplicate before launching any network work. This is a sample, not a pack crawl. */
object IconPackSamplePolicy {
    const val MAX_SAMPLES = 6

    fun select(channels: List<M3uChannel>, checkCancellation: () -> Unit = {}): IconPackSamplePlan {
        val selected = ArrayList<M3uChannel>(MAX_SAMPLES)
        val ids = HashSet<String>(MAX_SAMPLES)
        var missing = 0
        channels.forEachIndexed { index, channel ->
            if (index % CHECK_INTERVAL == 0) checkCancellation()
            val id = channel.tvgId
            if (id.isNullOrBlank()) missing++
            else if (selected.size < MAX_SAMPLES && ids.add(id)) selected.add(channel)
        }
        return IconPackSamplePlan(selected, missing, channels.size)
    }

    private const val CHECK_INTERVAL = 256
}
