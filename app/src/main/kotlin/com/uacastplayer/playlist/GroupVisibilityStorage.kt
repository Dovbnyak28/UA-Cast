package com.uacastplayer.playlist

/**
 * Persistence seam for group pin/hide overrides, implemented for real by
 * [com.uacastplayer.data.playlist.GroupVisibilityStore].
 *
 * It exists so [com.uacastplayer.app.GroupVisibilityController] can be exercised without a
 * `Context`: the controller owns genuinely non-obvious behaviour - per-source scoping, and the
 * one-shot migration of pre-source-scoping entries onto whichever source connects first - and none
 * of that was reachable from a plain JVM test while it depended on an `AtomicFile` under
 * `Context.filesDir`. Deliberately declared in this pure package rather than next to the
 * implementation, so a test can depend on it without pulling in Android.
 */
interface GroupVisibilityStorage {
    suspend fun load(): List<GroupVisibilityEntry>

    suspend fun save(entries: List<GroupVisibilityEntry>)
}
