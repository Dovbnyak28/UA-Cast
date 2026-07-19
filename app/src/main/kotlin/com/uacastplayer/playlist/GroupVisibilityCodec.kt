package com.uacastplayer.playlist

import com.uacastplayer.favorites.MiniJson

object GroupVisibilityCodec {

    fun encode(entries: List<GroupVisibilityEntry>): String =
        MiniJson.writeArrayOfObjects(
            entries.map { entry ->
                linkedMapOf(
                    "sourceId" to entry.sourceId,
                    "groupKey" to entry.groupKey,
                    "state" to entry.state.name,
                )
            }
        )

    fun decode(json: String): List<GroupVisibilityEntry> = try {
        MiniJson.parseArrayOfObjects(json).mapNotNull { fields ->
            val sourceId = fields["sourceId"] ?: return@mapNotNull null
            val groupKey = fields["groupKey"] ?: return@mapNotNull null
            val state = fields["state"]
                ?.let { name -> runCatching { GroupVisibilityState.valueOf(name) }.getOrNull() }
                ?: return@mapNotNull null
            GroupVisibilityEntry(sourceId, groupKey, state)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
