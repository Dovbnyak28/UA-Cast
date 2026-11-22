package app.zorik.player.ui.remote

import androidx.compose.runtime.compositionLocalOf

data class RemoteHeaderState(
    val connected: Boolean = false, val connecting: Boolean = false, val address: String? = null,
    val failed: Boolean = false,
)
val LocalRemoteHeaderState = compositionLocalOf { RemoteHeaderState() }
