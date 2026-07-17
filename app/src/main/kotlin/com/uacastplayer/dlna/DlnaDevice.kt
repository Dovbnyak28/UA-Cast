package com.uacastplayer.dlna

/** A discovered UPnP/DLNA renderer: its display name and the absolute URL of its AVTransport control endpoint. */
data class DlnaDevice(val friendlyName: String, val controlUrl: String)

/** Whatever the app is currently doing with a DLNA renderer - at most one connection at a time in this MVP. */
data class DlnaConnectionState(
    val connectedDevice: DlnaDevice? = null,
    val isConnecting: Boolean = false,
)
