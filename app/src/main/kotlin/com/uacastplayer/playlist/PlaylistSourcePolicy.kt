package com.uacastplayer.playlist

sealed class PlaylistSourceAddResult {
    data class Added(val sources: List<PlaylistSource>) : PlaylistSourceAddResult()
    /** At [PlaylistSourcePolicy.MAX_SOURCES] already, and [PlaylistSourcePolicy.add]'s incoming
     * source isn't just replacing one of the existing entries - the caller should tell the user
     * to remove one first rather than silently evicting their oldest saved source. */
    data object LimitReached : PlaylistSourceAddResult()
}

sealed class PlaylistSourceRemovalResult {
    data class Removed(val sources: List<PlaylistSource>, val newActiveId: String?) : PlaylistSourceRemovalResult()
    data object NotFound : PlaylistSourceRemovalResult()
}

/**
 * Pure add/remove logic for the saved playlist source list (see PlaylistSourceStore) - the actual
 * loading/switching is impure glue in AppViewModel, this only decides what the resulting list (and
 * active source, for removal) should look like.
 */
object PlaylistSourcePolicy {
    const val MAX_SOURCES = 10

    /** Adding a source whose [PlaylistSource.id] already exists replaces that entry in place
     * (e.g. re-adding the same URL refreshes its display name) - this never counts against the
     * limit, since the total source count doesn't change. */
    fun add(sources: List<PlaylistSource>, source: PlaylistSource): PlaylistSourceAddResult {
        val withoutExisting = sources.filterNot { it.id == source.id }
        if (withoutExisting.size >= MAX_SOURCES) return PlaylistSourceAddResult.LimitReached
        return PlaylistSourceAddResult.Added(withoutExisting + source)
    }

    /** Removing the active source falls back to the most recently added remaining one, or null if
     * that was the last source left - removing anything else leaves [activeId] untouched. */
    fun remove(sources: List<PlaylistSource>, activeId: String?, idToRemove: String): PlaylistSourceRemovalResult {
        if (sources.none { it.id == idToRemove }) return PlaylistSourceRemovalResult.NotFound
        val remaining = sources.filterNot { it.id == idToRemove }
        val newActiveId = if (activeId == idToRemove) {
            remaining.maxByOrNull { it.addedAtEpochMillis }?.id
        } else {
            activeId
        }
        return PlaylistSourceRemovalResult.Removed(remaining, newActiveId)
    }
}
