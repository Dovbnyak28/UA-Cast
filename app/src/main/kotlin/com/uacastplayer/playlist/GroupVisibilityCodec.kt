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

    /** A record with no `sourceId` field at all (format version 1 - see [LEGACY_SOURCE_ID]) is
     * tagged [LEGACY_SOURCE_ID] rather than dropped, so a pre-source-scoping pin/hide list isn't
     * silently destroyed by decoding it under the current format - see
     * `app/GroupVisibilityController`'s migration. */
    fun decode(json: String): List<GroupVisibilityEntry> = try {
        MiniJson.parseArrayOfObjects(json).mapNotNull { fields ->
            val sourceId = fields["sourceId"] ?: LEGACY_SOURCE_ID
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
