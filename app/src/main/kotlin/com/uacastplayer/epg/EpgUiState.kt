package com.uacastplayer.epg

data class EpgUiState(
    val data: EpgData? = null,
    val isLoading: Boolean = false,
    val selectedSource: EpgSource = EpgSource.DEFAULT,
    val nowMillis: Long = System.currentTimeMillis(),
)
