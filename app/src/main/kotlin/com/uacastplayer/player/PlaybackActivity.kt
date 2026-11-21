package com.uacastplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Process-wide "playback needs network priority" signal, contributed by [PlayerViewModel] and the
 * application-owned remote adapter, and read by [com.uacastplayer.app.IconController] to hold
 * off the background icon prefetch while it would compete with playback/scroll for CPU and network.
 * A plain object rather than DI'd through AppViewModel because the player and the icon prefetcher
 * live in two different ViewModels with no direct reference to each other.
 */
object PlaybackActivity {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    private var playerActive = false
    private val remoteOwners = mutableSetOf<Any>()

    @Synchronized fun setActive(active: Boolean) {
        playerActive = active
        publish()
    }

    /** Bound by the application adapter, never by a screen/ViewModel. Cancellation releases only its lease. */
    fun observeRemote(scope: CoroutineScope, source: Flow<Boolean>): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val owner = Any()
            try {
                source.collect { setRemote(owner, it) }
            } finally {
                setRemote(owner, false)
            }
        }

    @Synchronized private fun setRemote(owner: Any, active: Boolean) {
        if (active) remoteOwners.add(owner) else remoteOwners.remove(owner)
        publish()
    }

    private fun publish() {
        _isActive.value = playerActive || remoteOwners.isNotEmpty()
    }
}
