package com.uacastplayer.player

import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.dlna.DlnaSessionRepository
import com.uacastplayer.playlist.M3uChannel

/** User-initiated DLNA operations, separate from PlayerViewModel's Media3 wiring. */
class PlayerDlnaController(
    private val repository: DlnaSessionRepository,
    private val beforeConnect: () -> Unit = {},
    private val currentChannel: () -> M3uChannel?,
) {
    suspend fun discoverDevices(): List<DlnaDevice> = repository.discoverDevices()

    /** No-op with no channel loaded - there would be nothing to hand the renderer. */
    fun connect(device: DlnaDevice) {
        val channel = currentChannel() ?: return
        beforeConnect()
        repository.connect(
            device, channel.streamUrl, channel.displayName, channel.userAgent, channel.referrer, channel.localVideo,
        )
    }

    fun stop() = repository.stop()

    /** A delayed UI gesture must still belong to the renderer shown when it began. */
    fun setVolume(device: DlnaDevice, volume: Int) = repository.setVolume(device, volume)
}
