package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Serializes fire-and-forget persistence while coalescing obsolete states.
 *
 * Launching one coroutine per UI mutation allows two writes to the same AtomicFile to overlap and
 * allows an older write to finish last. This actor completes the active write, retains only the
 * newest state submitted while it is busy, then writes that state next. Intermediate snapshots are
 * intentionally disposable; only the latest state is meaningful after a process restart.
 */
internal class LatestValueWriter<T>(
    scope: CoroutineScope,
    write: suspend (T) -> Unit,
    onWriteFailure: (Throwable) -> Unit = {},
) {
    private data class Update<T>(val sequence: Long, val value: T)
    private data class Completion(val sequence: Long = 0, val successful: Boolean = true, val closed: Boolean = false)
    private val updates = Channel<Update<T>>(capacity = Channel.CONFLATED)
    private val submitLock = Any()
    private var submitted = 0L
    private val completion = MutableStateFlow(Completion())

    init {
        scope.launch {
            for (update in updates) {
                // A single failed disk/provider write must not retire the only consumer forever:
                // later UI mutations still need a chance to become durable. Cancellation and fatal
                // errors escape runCatchingNonFatal and retain normal scope/VM semantics.
                val result = runCatchingNonFatal { write(update.value) }.onFailure(onWriteFailure)
                completion.value = Completion(update.sequence, result.isSuccess)
            }
        }.invokeOnCompletion { completion.value = completion.value.copy(closed = true) }
    }

    fun submit(value: T) {
        synchronized(submitLock) {
            submitted++
            updates.trySend(Update(submitted, value))
        }
    }

    /** Waits for this snapshot or a newer conflated snapshot to become durable, not just queued. */
    suspend fun awaitPending(): Boolean {
        val target = synchronized(submitLock) { submitted }
        val result = completion.first { it.sequence >= target || it.closed }
        return result.sequence >= target && result.successful
    }

    /** Primarily for finite owners/tests. Application and ViewModel scopes cancel the collector. */
    fun close() {
        updates.close()
    }
}
