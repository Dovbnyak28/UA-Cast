package com.uacastplayer.playlist

sealed class PlaylistError {
    data object SizeLimitExceeded : PlaylistError()
    data class Http(val code: Int) : PlaylistError()
    data object Network : PlaylistError()

    /**
     * The source was read and held no channels.
     *
     * Distinct from every other case here because nothing failed: a zero-byte file, a JPEG picked
     * by mistake, an M3U whose every line was skipped. It used to resolve to an ordinary success
     * with an empty list, which the app renders as the same screen a user with no playlist at all
     * sees - so the answer to "I added my playlist and nothing happened" was a blank screen and no
     * sentence anywhere saying the file had nothing in it.
     */
    data object Empty : PlaylistError()
}

data class PlaylistUiState(
    val groups: List<GroupedChannels> = emptyList(),
    val isLoading: Boolean = false,
    val skippedLineCount: Int = 0,
    val error: PlaylistError? = null,
    /** Short, non-reversible id for the loaded playlist's source, shown on the Home dashboard. */
    val activePlaylistId: String? = null,
    /** True when [groups] came from the on-disk snapshot at startup, not a fresh network/file load. */
    val restoredFromCache: Boolean = false,
    /** User-chosen label for the active playlist, shown instead of [activePlaylistId] when set. */
    val displayName: String? = null,
    /** The URL the active playlist was loaded from - null for a file import. Lets the UI offer a
     * one-tap refresh instead of sending the user back through Settings to retype it. */
    val sourceUrl: String? = null,
) {
    val hasChannels: Boolean get() = groups.isNotEmpty()
}
