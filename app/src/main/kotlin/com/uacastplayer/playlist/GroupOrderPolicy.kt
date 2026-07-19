package com.uacastplayer.playlist

/**
 * Orders the groups overview grid by user pin/hide choices (see
 * `data/playlist/GroupVisibilityStore.kt`, keyed by [groupDisplayKey]): pinned groups first (in
 * their original relative order), then everything else, with hidden groups excluded entirely -
 * they're still reachable via global search, just not shown in the overview.
 */
object GroupOrderPolicy {

    fun order(groups: List<GroupedChannels>, pinnedKeys: Set<String>, hiddenKeys: Set<String>): List<GroupedChannels> {
        val visible = groups.filterNot { groupDisplayKey(it.group) in hiddenKeys }
        val (pinned, rest) = visible.partition { groupDisplayKey(it.group) in pinnedKeys }
        return pinned + rest
    }
}
