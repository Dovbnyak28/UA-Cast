package com.uacastplayer.ui.player

import androidx.lifecycle.ViewModel
import com.uacastplayer.player.PlayerRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains only the navigation request across Activity recreation, never an Activity or engine.
 * Process restoration uses a separate small saved marker, not a channel list in a Bundle. */
class PlayerRequestViewModel : ViewModel() {
    private val _request = MutableStateFlow<PlayerRequest?>(null)
    val request = _request.asStateFlow()

    fun open(request: PlayerRequest) { _request.value = request }
    fun close() { _request.value = null }
}
