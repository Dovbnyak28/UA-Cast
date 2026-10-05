package com.uacastplayer.app

import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceSaveState
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns ordered durable writes and post-commit cleanup, using the controller's existing scope.
 * Pending deletions survive a failed write. A superseding re-add retains its snapshot. */
class PlaylistSourcePersistence internal constructor(
    private val scope: CoroutineScope,
    private val repository: PlaylistRepository,
    private val currentSources: () -> List<PlaylistSource>,
    private val publish: (PlaylistSourceSaveState) -> Unit,
) {
    private val mutex = Mutex()
    private val generation = AtomicLong()
    private val result = MutableStateFlow(0L to true)
    private val pendingDeletes = mutableSetOf<String>()

    fun markForDeletion(id: String) { pendingDeletes += id }

    fun write(sources: List<PlaylistSource>): Job {
        val requested = generation.incrementAndGet()
        publish(PlaylistSourceSaveState.SAVING)
        return scope.launch {
            mutex.withLock {
                if (requested == generation.get()) {
                    val saved = repository.saveSources(sources)
                    if (requested == generation.get()) {
                        publish(if (saved) PlaylistSourceSaveState.SAVED else PlaylistSourceSaveState.FAILED)
                        if (saved) deleteCommittedSnapshots(sources)
                    }
                    result.value = requested to saved
                }
            }
        }
    }

    fun retry() {
        if (!result.value.second) write(currentSources())
    }

    suspend fun awaitPersistence(): Boolean {
        val target = generation.get()
        return result.first { it.first >= target }.second
    }

    private suspend fun deleteCommittedSnapshots(sources: List<PlaylistSource>) {
        val retained = sources.mapTo(mutableSetOf()) { it.id }
        val deleted = pendingDeletes.filter { it !in retained }
        pendingDeletes.clear()
        for (id in deleted) {
            if (currentSources().none { it.id == id }) repository.deleteSnapshot(id)
        }
    }
}
