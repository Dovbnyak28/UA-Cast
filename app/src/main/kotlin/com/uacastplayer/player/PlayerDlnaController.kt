package com.uacastplayer.player

import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.playlist.M3uChannel

/** User-initiated DLNA operations, separate from PlayerViewModel's Media3 wiring. */
class PlayerDlnaController(
    private val repository: DlnaSessionRepository,
    private val currentChannel: () -> M3uChannel?,
) {
    suspend fun discoverDevices(): List<DlnaDevice> = repository.discoverDevices()

    /** No-op with no channel loaded - there would be nothing to hand the renderer. */
    fun connect(device: DlnaDevice) {
        val channel = currentChannel() ?: return
        repository.connect(device, channel.streamUrl, channel.displayName)
    }

    fun stop() = repository.stop()

    /** No-op if nothing is connected or the renderer has no RenderingControl service. */
    fun setVolume(volume: Int) = repository.setVolume(volume)
}
