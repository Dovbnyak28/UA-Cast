package com.uacastplayer.app

import com.uacastplayer.core.concurrent.LatestValueWriter
import com.uacastplayer.playlist.GroupVisibilityEntry
import com.uacastplayer.playlist.GroupVisibilityStorage
import com.uacastplayer.playlist.GroupVisibilityState
import com.uacastplayer.playlist.LEGACY_SOURCE_ID
import com.uacastplayer.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "GroupVisibilityController"

/**
 * Owns per-playlist-source group pin/hide overrides (see [GroupVisibilityStorage]) - moved out of
 * [com.uacastplayer.AppViewModel] as a move-only split, same pattern as the other controllers (see
 * B1 in the consolidated fix plan). [pinnedKeys]/[hiddenKeys] always reflect whichever source is
 * currently active (see [setActiveSource]) - switching playlists never mixes another source's
 * pins/hides into the current one.
 */
class GroupVisibilityController(
    private val store: GroupVisibilityStorage,
    private val scope: CoroutineScope,
) {
    private val writer = LatestValueWriter(scope, store::save) { error ->
        AppLog.w(TAG) { "Group visibility persistence failed: ${error.javaClass.simpleName}" }
    }
    private var allEntries: List<GroupVisibilityEntry> = emptyList()
    private var activeSourceId: String? = null
    private var initialLoadFinished = false
    private val pendingOverrides = linkedMapOf<Pair<String, String>, GroupVisibilityEntry?>()

    private val _pinnedKeys = MutableStateFlow<Set<String>>(emptySet())
    val pinnedKeys: StateFlow<Set<String>> = _pinnedKeys.asStateFlow()

    private val _hiddenKeys = MutableStateFlow<Set<String>>(emptySet())
    val hiddenKeys: StateFlow<Set<String>> = _hiddenKeys.asStateFlow()

    fun loadInitial() {
        scope.launch {
            val loaded = store.load()
            val hadPendingMutation = pendingOverrides.isNotEmpty()
            val merged = loaded.associateByTo(linkedMapOf()) { it.sourceId to it.groupKey }
            for ((key, override) in pendingOverrides) {
                if (override == null) merged.remove(key) else merged[key] = override
            }
            pendingOverrides.clear()
            allEntries = merged.values.toList()
            initialLoadFinished = true
            val sourceId = activeSourceId
            if (sourceId != null && allEntries.any { it.sourceId == LEGACY_SOURCE_ID }) {
                migrateLegacyEntries(sourceId)
            } else if (hadPendingMutation) {
                writer.submit(allEntries)
            }
            refreshActiveSource()
        }
    }

    fun setActiveSource(sourceId: String?) {
        activeSourceId = sourceId
        if (sourceId != null) migrateLegacyEntries(sourceId)
        refreshActiveSource()
    }

    /** A record decoded from a pre-source-scoping file (format version 1) is tagged
     * [LEGACY_SOURCE_ID] rather than dropped (see [com.uacastplayer.playlist.GroupVisibilityCodec.decode]) -
     * migrated onto whichever source connects first, since that's the best available guess for
     * whose pin/hide list it originally was (the old format only ever supported one playlist at a
     * time). A no-op once migrated: no more [LEGACY_SOURCE_ID] entries remain to match. */
    private fun migrateLegacyEntries(targetSourceId: String) {
        if (allEntries.none { it.sourceId == LEGACY_SOURCE_ID }) return
        allEntries = allEntries.map { if (it.sourceId == LEGACY_SOURCE_ID) it.copy(sourceId = targetSourceId) else it }
        if (initialLoadFinished) writer.submit(allEntries)
    }

    fun pinGroup(groupKey: String) = setState(groupKey, GroupVisibilityState.PINNED)

    fun hideGroup(groupKey: String) = setState(groupKey, GroupVisibilityState.HIDDEN)

    /** Clears any override (pin or hide) for [groupKey] in the active source, returning it to the
     * default/normal state - used both to unpin and to restore a hidden group. */
    fun clearOverride(groupKey: String) {
        val sourceId = activeSourceId ?: return
        allEntries = allEntries.filterNot { it.sourceId == sourceId && it.groupKey == groupKey }
        if (!initialLoadFinished) pendingOverrides[sourceId to groupKey] = null
        persistAndRefresh()
    }

    private fun setState(groupKey: String, state: GroupVisibilityState) {
        val sourceId = activeSourceId ?: return
        val updatedEntry = GroupVisibilityEntry(sourceId, groupKey, state)
        allEntries = allEntries.filterNot { it.sourceId == sourceId && it.groupKey == groupKey } + updatedEntry
        if (!initialLoadFinished) pendingOverrides[sourceId to groupKey] = updatedEntry
        persistAndRefresh()
    }

    private fun persistAndRefresh() {
        refreshActiveSource()
        if (initialLoadFinished) writer.submit(allEntries)
    }

    private fun refreshActiveSource() {
        val sourceId = activeSourceId
        val forSource = if (sourceId == null) emptyList() else allEntries.filter { it.sourceId == sourceId }
        _pinnedKeys.value = forSource.filter { it.state == GroupVisibilityState.PINNED }
            .mapTo(mutableSetOf()) { it.groupKey }
        _hiddenKeys.value = forSource.filter { it.state == GroupVisibilityState.HIDDEN }
            .mapTo(mutableSetOf()) { it.groupKey }
    }
}
