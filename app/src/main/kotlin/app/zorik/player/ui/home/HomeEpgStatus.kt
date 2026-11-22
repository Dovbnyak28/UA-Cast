package app.zorik.player.ui.home

import app.zorik.player.R
import app.zorik.player.epg.EpgUiState

/** A cached guide may remain visible after a failed refresh; the failure must still be reported. */
internal fun homeEpgStatus(state: EpgUiState): Int = when {
    state.isLoading -> R.string.home_epg_restoring
    state.hasError -> R.string.home_epg_unavailable
    state.data != null -> R.string.home_epg_ready
    state.customUrl.isNullOrBlank() -> R.string.home_epg_not_configured
    else -> R.string.home_epg_waiting
}
