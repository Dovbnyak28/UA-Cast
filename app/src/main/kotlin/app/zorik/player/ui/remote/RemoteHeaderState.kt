package app.zorik.player.ui.remote

import androidx.compose.runtime.compositionLocalOf

data class RemoteHeaderState(
    val connected: Boolean = false, val connecting: Boolean = false, val address: String? = null,
    val failed: Boolean = false,
) {
    /** A remote session is in progress (or just failed): the phone header shows its status then. */
    val active: Boolean get() = connected || connecting || failed
}
val LocalRemoteHeaderState = compositionLocalOf { RemoteHeaderState() }
