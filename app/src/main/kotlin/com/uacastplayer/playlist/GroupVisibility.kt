package com.uacastplayer.playlist

/** A group not present in any stored entry is neither - the normal, default state. */
enum class GroupVisibilityState { PINNED, HIDDEN }

/** One user override of a group's default visibility/order, scoped to the playlist source it was
 * made in (see `data/playlist/GroupVisibilityStore.kt`) - switching playlists never mixes another
 * source's pins/hides into the current one. [groupKey] is [groupDisplayKey]. */
data class GroupVisibilityEntry(val sourceId: String, val groupKey: String, val state: GroupVisibilityState)

/** Sentinel [GroupVisibilityEntry.sourceId] for an entry decoded from a file written before
 * source-scoping existed (format version 1 - see `data/playlist/GroupVisibilityStore.kt`), where
 * pins/hides were a single flat list with no source concept at all. [GroupVisibilityCodec.decode]
 * tags a record with a missing `sourceId` field this way instead of dropping it, so
 * `app/GroupVisibilityController` can migrate it onto whichever source is active the first time one
 * connects - not a real source id itself, never written back to disk once migrated. */
internal const val LEGACY_SOURCE_ID = ""
