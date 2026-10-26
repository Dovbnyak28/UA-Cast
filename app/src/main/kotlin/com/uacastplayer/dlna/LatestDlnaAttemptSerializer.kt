package com.uacastplayer.dlna

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes DLNA transport command sequences and retires a sequence as soon as a newer generation
 * exists.
 *
 * OkHttp's synchronous SOAP calls do not stop halfway through a blocking read when their coroutine
 * is cancelled. Without serialization, a second channel switch can therefore finish first and an
 * older `SetAVTransportURI` can land afterwards, putting the TV back on the previous channel. The
 * mutex makes command order deterministic; [checkpoint] lets the old owner stop between blocking
 * calls as soon as it regains control.
 */
internal class LatestDlnaAttemptSerializer {
    private val mutex = Mutex()
    private val lifecycleLock = Any()

    suspend fun <T> run(
        generation: Long,
        currentGeneration: () -> Long,
        block: suspend (checkpoint: suspend () -> Unit) -> T,
    ): T = mutex.withLock {
        ensureCurrent(generation, currentGeneration)
        block { ensureCurrent(generation, currentGeneration) }
    }

    /** Queues an unconditional teardown in the same command order as generation-owned attempts. */
    suspend fun <T> runSerialized(block: suspend () -> T): T = mutex.withLock { block() }

    /** Serializes proxy/service setup and teardown across the main and IO threads. */
    fun <T> withLifecycle(block: () -> T): T = synchronized(lifecycleLock, block)

    /** Claims the lifecycle only if [generation] still owns it at the instant the lock is taken. */
    fun <T> withCurrentLifecycle(
        generation: Long,
        currentGeneration: () -> Long,
        block: () -> T,
    ): T = synchronized(lifecycleLock) {
        ensureGenerationCurrent(generation, currentGeneration)
        block()
    }

    private suspend fun ensureCurrent(generation: Long, currentGeneration: () -> Long) {
        currentCoroutineContext().ensureActive()
        ensureGenerationCurrent(generation, currentGeneration)
    }

    private fun ensureGenerationCurrent(generation: Long, currentGeneration: () -> Long) {
        if (generation != currentGeneration()) throw CancellationException("DLNA attempt was superseded")
    }
}
