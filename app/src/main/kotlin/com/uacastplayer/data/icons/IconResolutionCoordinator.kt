package com.uacastplayer.data.icons

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rows and prefetch share one resolution lane per channel/cache revision. Waiting is cancellable;
 * cancelling one caller never cancels another. A new revision or channel uses an independent lane.
 * No detached scope/jobs and no idle entries retained for thousands of previously visited channels.
 */
internal class IconResolutionCoordinator {
    private data class Key(val channelId: String, val generation: Long)
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = mutableMapOf<Key, Entry>()

    suspend fun <T> withResolution(channelId: String, generation: Long, resolve: suspend () -> T): T {
        val key = Key(channelId, generation)
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        return try {
            entry.mutex.withLock { resolve() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(key)
            }
        }
    }

    internal fun entryCountForTesting(): Int = synchronized(entries) { entries.size }
}
