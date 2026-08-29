package com.uacastplayer.data.playlist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orders snapshot writes and deletion independently for each playlist source.
 *
 * Cancelling a playlist load cannot interrupt an AtomicFile write already executing on IO. A
 * source deletion must therefore both retire the load's captured generation and wait behind any
 * physical write already in progress; otherwise the cancelled writer can recreate the snapshot
 * after deletion. A later re-add captures the new generation and may persist normally.
 */
internal class PlaylistSnapshotMutationCoordinator {
    internal data class WriteLease(val sourceId: String, val generation: Long)

    private class Entry {
        val generation = AtomicLong(0)
        val mutex = Mutex()
        val activeUsers = AtomicInteger(0)
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun captureWrite(sourceId: String): WriteLease {
        val entry = acquireEntry(sourceId)
        return WriteLease(sourceId, entry.generation.get())
    }

    suspend fun runWriteIfCurrent(lease: WriteLease, write: suspend () -> Unit): Boolean {
        val entry = entries[lease.sourceId]
        if (entry == null) return false
        return try {
            entry.mutex.withLock {
                if (lease.generation != entry.generation.get()) {
                    false
                } else {
                    write()
                    true
                }
            }
        } finally {
            release(lease.sourceId, entry)
        }
    }

    suspend fun invalidateAndDelete(sourceId: String, delete: suspend () -> Unit) {
        val entry = acquireEntry(sourceId)
        val deleteGeneration = entry.generation.incrementAndGet()
        try {
            entry.mutex.withLock {
                // Two deletes are equivalent. Let only the newest waiter touch disk; most importantly,
                // neither can be overtaken by a write carrying an older captured generation.
                if (deleteGeneration == entry.generation.get()) delete()
            }
        } finally {
            release(sourceId, entry)
        }
    }

    private fun acquireEntry(sourceId: String): Entry = synchronized(entries) {
        entries.computeIfAbsent(sourceId) { Entry() }.also { it.activeUsers.incrementAndGet() }
    }

    private fun release(sourceId: String, entry: Entry) {
        synchronized(entries) {
            if (entry.activeUsers.decrementAndGet() != 0 || entries.size <= MAX_TRACKED_ENTRIES) return
            // An entry with a captured lease is never removed. Once no operation refers to it,
            // its generation can no longer protect a write, so dropping it is safe and bounds
            // memory for sessions that try many one-off playlist URLs.
            entries.entries
                .asSequence()
                .filter { (_, candidate) -> candidate.activeUsers.get() == 0 }
                .take(entries.size - MAX_TRACKED_ENTRIES)
                .forEach { (candidateId, candidate) -> entries.remove(candidateId, candidate) }
            // Prefer removing the just-finished entry when it is one of the idle candidates. This
            // keeps one-off URLs from dominating the map while preserving hot sources naturally.
            if (entries.size > MAX_TRACKED_ENTRIES && entry.activeUsers.get() == 0) {
                entries.remove(sourceId, entry)
            }
        }
    }

    internal fun entryCountForTesting(): Int = entries.size

    private companion object {
        const val MAX_TRACKED_ENTRIES = 256
    }
}
