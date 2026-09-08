package com.uacastplayer.dlna

/** Pure guards for a renderer handoff and its asynchronous state commit. */
internal object DlnaConnectionAttemptPolicy {
    fun previousDevices(
        pendingDevice: DlnaDevice?,
        connectedDevice: DlnaDevice?,
        targetDevice: DlnaDevice,
    ): List<DlnaDevice> = listOfNotNull(pendingDevice, connectedDevice)
        .distinctBy { it.controlUrl }
        .filter { it.controlUrl != targetDevice.controlUrl }

    fun shouldCommit(generation: Long, currentGeneration: Long): Boolean =
        generation == currentGeneration
}
