package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
    private val updates = Channel<T>(capacity = Channel.CONFLATED)

    init {
        scope.launch {
            for (value in updates) {
                // A single failed disk/provider write must not retire the only consumer forever:
                // later UI mutations still need a chance to become durable. Cancellation and fatal
                // errors escape runCatchingNonFatal and retain normal scope/VM semantics.
                runCatchingNonFatal { write(value) }.onFailure(onWriteFailure)
            }
        }
    }

    fun submit(value: T) {
        updates.trySend(value)
    }

    /** Primarily for finite owners/tests. Application and ViewModel scopes cancel the collector. */
    fun close() {
        updates.close()
    }
}
