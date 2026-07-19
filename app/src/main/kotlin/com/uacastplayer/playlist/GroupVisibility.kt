package com.uacastplayer.playlist

/** A group not present in any stored entry is neither - the normal, default state. */
enum class GroupVisibilityState { PINNED, HIDDEN }

/** One user override of a group's default visibility/order, scoped to the playlist source it was
 * made in (see `data/playlist/GroupVisibilityStore.kt`) - switching playlists never mixes another
 * source's pins/hides into the current one. [groupKey] is [groupDisplayKey]. */
data class GroupVisibilityEntry(val sourceId: String, val groupKey: String, val state: GroupVisibilityState)
