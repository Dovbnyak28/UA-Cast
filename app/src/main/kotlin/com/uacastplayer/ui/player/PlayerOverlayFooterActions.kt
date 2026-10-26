package com.uacastplayer.ui.player

internal data class PlayerOverlayFooterActions(
    val playPause: () -> Unit, val next: () -> Unit, val previous: () -> Unit,
    val fullscreen: () -> Unit, val pip: () -> Unit, val sleepTimer: () -> Unit,
)
