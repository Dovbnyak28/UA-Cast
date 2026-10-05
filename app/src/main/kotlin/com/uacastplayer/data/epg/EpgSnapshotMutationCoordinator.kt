package com.uacastplayer.data.epg

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ensures the single EPG snapshot is written by the newest source request only. */
internal class EpgSnapshotMutationCoordinator {
    internal data class WriteLease(val generation: Long)

    private val generation = AtomicLong(0)
    private val mutex = Mutex()

    /** Starts a source/restore operation and retires every older snapshot writer. */
    fun begin(): WriteLease = WriteLease(generation.incrementAndGet())

    suspend fun runWriteIfCurrent(lease: WriteLease, write: suspend () -> Unit): Boolean =
        mutex.withLock {
            if (lease.generation != generation.get()) {
                false
            } else {
                write()
                true
            }
        }

    suspend fun invalidateAndDelete(delete: suspend () -> Unit) {
        val deleteGeneration = generation.incrementAndGet()
        mutex.withLock {
            if (deleteGeneration == generation.get()) delete()
        }
    }
}
