package com.uacastplayer.data.playlist

import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState

private const val ACTIVE_PLAYLIST_ID_LENGTH = 8

/**
 * Reduces a [PlaylistOutcome] into the next [PlaylistUiState]. A failed *reload*
 * (SizeLimitExceeded/HttpError/ReadError) deliberately keeps whatever channels/sourceUrl/
 * displayName [current] already had - only [PlaylistUiState.isLoading] and
 * [PlaylistUiState.error] change - so a transient network hiccup while refreshing an
 * already-loaded playlist doesn't lock the user out of channels they already have.
 */
object PlaylistOutcomeReducer {

    fun reduce(
        current: PlaylistUiState,
        outcome: PlaylistOutcome,
        fromCache: Boolean,
        displayName: String?,
    ): PlaylistUiState = when (outcome) {
        // Read successfully, and empty. Kept apart from the Loaded branch below rather than folded
        // into it: everything downstream treats "no groups" as "no playlist yet", which is the
        // right answer for a fresh install and the wrong one for a file the user just chose.
        is PlaylistOutcome.Loaded if outcome.groups.all { it.channels.isEmpty() } ->
            current.copy(isLoading = false, error = PlaylistError.Empty)
        is PlaylistOutcome.Loaded -> PlaylistUiState(
            groups = outcome.groups,
            isLoading = false,
            skippedLineCount = outcome.skippedLineCount,
            error = null,
            activePlaylistId = outcome.sourceFingerprint?.take(ACTIVE_PLAYLIST_ID_LENGTH),
            restoredFromCache = fromCache,
            displayName = displayName,
            sourceUrl = outcome.sourceUrl,
        )
        PlaylistOutcome.SizeLimitExceeded -> current.copy(isLoading = false, error = PlaylistError.SizeLimitExceeded)
        is PlaylistOutcome.HttpError -> current.copy(isLoading = false, error = PlaylistError.Http(outcome.code))
        is PlaylistOutcome.ReadError -> current.copy(isLoading = false, error = PlaylistError.Network)
    }
}
