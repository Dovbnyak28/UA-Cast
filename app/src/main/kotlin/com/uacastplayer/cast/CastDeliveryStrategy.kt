package com.uacastplayer.cast

sealed class CastDeliveryMode {
    data object Direct : CastDeliveryMode()
    data object Proxy : CastDeliveryMode()
}

/**
 * Decides direct-vs-proxy delivery. Direct is always tried first unless the stream+receiver pair
 * is already known to be incompatible; once on Proxy there's nowhere further to fall back to, so
 * a subsequent failure there is terminal.
 */
object CastDeliveryStrategy {

    fun initialMode(isKnownIncompatible: Boolean): CastDeliveryMode =
        if (isKnownIncompatible) CastDeliveryMode.Proxy else CastDeliveryMode.Direct

    fun onDirectFailure(currentMode: CastDeliveryMode): CastDeliveryMode =
        if (currentMode == CastDeliveryMode.Direct) CastDeliveryMode.Proxy else currentMode

    fun onWatchdogTimeout(currentMode: CastDeliveryMode, receiverStatus: ReceiverStatus): CastDeliveryMode =
        if (currentMode == CastDeliveryMode.Direct && receiverStatus != ReceiverStatus.PLAYING) {
            CastDeliveryMode.Proxy
        } else {
            currentMode
        }

    fun isTerminalFailure(mode: CastDeliveryMode): Boolean = mode == CastDeliveryMode.Proxy
}
