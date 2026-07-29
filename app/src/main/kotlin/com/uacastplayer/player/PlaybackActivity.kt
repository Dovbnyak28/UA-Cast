package com.uacastplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "is something actually playing right now" signal, set by [PlayerViewModel] (local
 * playback or an active cast session) and read by [com.uacastplayer.app.IconController] to hold
 * off the background icon prefetch while it would compete with playback/scroll for CPU and network.
 * A plain object rather than DI'd through AppViewModel because the player and the icon prefetcher
 * live in two different ViewModels with no direct reference to each other.
 */
object PlaybackActivity {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun setActive(active: Boolean) {
        _isActive.value = active
    }
}
