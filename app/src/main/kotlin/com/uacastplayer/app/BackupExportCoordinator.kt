package com.uacastplayer.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A SAF document may have independently positioned truncating output handles. Keep exports to
 * the same URI exclusive until the previous handle closes, without blocking unrelated documents.
 * Only active/waiting callers retain entries; no Context, detached jobs, or IO threads are owned.
 */
internal class BackupExportCoordinator {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withDestination(destination: String, write: suspend () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(destination) { Entry() }.also { it.users++ }
        }
        return try {
            entry.mutex.withLock { write() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(destination)
            }
        }
    }

    internal fun entryCountForTesting(): Int = synchronized(entries) { entries.size }
}
