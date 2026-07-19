package com.uacastplayer.app

import com.uacastplayer.data.playlist.GroupVisibilityStore
import com.uacastplayer.playlist.GroupVisibilityEntry
import com.uacastplayer.playlist.GroupVisibilityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns per-playlist-source group pin/hide overrides (see [GroupVisibilityStore]) - moved out of
 * [com.uacastplayer.AppViewModel] as a move-only split, same pattern as the other controllers (see
 * B1 in the consolidated fix plan). [pinnedKeys]/[hiddenKeys] always reflect whichever source is
 * currently active (see [setActiveSource]) - switching playlists never mixes another source's
 * pins/hides into the current one.
 */
class GroupVisibilityController(
    private val store: GroupVisibilityStore,
    private val scope: CoroutineScope,
) {
    private var allEntries: List<GroupVisibilityEntry> = emptyList()
    private var activeSourceId: String? = null

    private val _pinnedKeys = MutableStateFlow<Set<String>>(emptySet())
    val pinnedKeys: StateFlow<Set<String>> = _pinnedKeys.asStateFlow()

    private val _hiddenKeys = MutableStateFlow<Set<String>>(emptySet())
    val hiddenKeys: StateFlow<Set<String>> = _hiddenKeys.asStateFlow()

    fun loadInitial() {
        scope.launch {
            allEntries = store.load()
            refreshActiveSource()
        }
    }

    fun setActiveSource(sourceId: String?) {
        activeSourceId = sourceId
        refreshActiveSource()
    }

    fun pinGroup(groupKey: String) = setState(groupKey, GroupVisibilityState.PINNED)

    fun hideGroup(groupKey: String) = setState(groupKey, GroupVisibilityState.HIDDEN)

    /** Clears any override (pin or hide) for [groupKey] in the active source, returning it to the
     * default/normal state - used both to unpin and to restore a hidden group. */
    fun clearOverride(groupKey: String) {
        val sourceId = activeSourceId ?: return
        allEntries = allEntries.filterNot { it.sourceId == sourceId && it.groupKey == groupKey }
        persistAndRefresh()
    }

    private fun setState(groupKey: String, state: GroupVisibilityState) {
        val sourceId = activeSourceId ?: return
        allEntries = allEntries.filterNot { it.sourceId == sourceId && it.groupKey == groupKey } +
            GroupVisibilityEntry(sourceId, groupKey, state)
        persistAndRefresh()
    }

    private fun persistAndRefresh() {
        refreshActiveSource()
        scope.launch { store.save(allEntries) }
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
