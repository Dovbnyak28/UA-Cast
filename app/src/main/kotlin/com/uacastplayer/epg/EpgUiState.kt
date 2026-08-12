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
    /**
     * Why the last attempt failed, in the words of
     * [com.uacastplayer.data.epg.EpgFailureReason] - null once one succeeds.
     *
     * [hasError] is what the UI needs and all it needs; this is for the diagnostics report, which
     * is read by somebody who was not there and cannot ask. Kept beside `hasError` rather than in
     * the report builder because this is the only place the outcome still exists: by the time a
     * report is built, the failure is minutes old and the object that described it is long gone.
     */
    val lastFailure: String? = null,
)
