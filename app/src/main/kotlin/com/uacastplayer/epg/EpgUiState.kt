package com.uacastplayer.epg

data class EpgUiState(
    val data: EpgData? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val selectedSource: EpgSource = EpgSource.DEFAULT,
    /** Non-null when a playlist/Xtream-provided URL (see [EpgSourceAutoDetect]) is active instead
     * of [selectedSource]. */
    val customUrl: String? = null,
    /** A playlist-provided EPG URL not yet applied - see [EpgSourceAutoDetect.Action.Suggest].
     * Shown in Settings with a "Use" action. */
    val suggestedUrl: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
)
