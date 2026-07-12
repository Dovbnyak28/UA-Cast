package com.uacastplayer.playlist

sealed class PlaylistError {
    data object SizeLimitExceeded : PlaylistError()
    data class Http(val code: Int) : PlaylistError()
    data object Network : PlaylistError()
}

data class PlaylistUiState(
    val groups: List<GroupedChannels> = emptyList(),
    val isLoading: Boolean = false,
    val skippedLineCount: Int = 0,
    val error: PlaylistError? = null,
) {
    val hasChannels: Boolean get() = groups.isNotEmpty()
}
