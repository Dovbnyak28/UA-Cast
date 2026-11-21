package com.uacastplayer.app

import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceLabel
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.playlist.PlaylistUiState

/**
 * Stateless decisions behind [PlaylistController]: pure functions of the saved source list, the
 * active id and a load outcome. The controller keeps every piece of mutable state; this only
 * computes the values it publishes.
 */
internal object PlaylistSourceTransitions {

    fun newPendingSource(type: PlaylistSourceType, location: String): PlaylistSource = PlaylistSource(
        id = Fingerprint.of(location),
        type = type,
        location = location,
        displayName = null,
        addedAtEpochMillis = System.currentTimeMillis(),
    )

    /**
     * What the user typed, else what the source already knows about itself (a picked file carries
     * its own name - see loadPlaylistFromFile), else one derived from where it came from. The last
     * step used to be an Xtream-only special case; PlaylistSourceLabel answers for every type,
     * which is what stops a URL playlist falling through to its own SHA-256.
     */
    fun effectiveDisplayName(typedName: String, pending: PlaylistSource): String? =
        typedName.ifBlank { null }
            ?: pending.displayName
            ?: PlaylistSourceLabel.forLocation(pending.type, pending.location)

    /** The preferred id while it still names a saved source, else the first saved source. */
    fun resolveActiveId(preferredId: String?, sources: List<PlaylistSource>): String? =
        preferredId?.takeIf { id -> sources.any { it.id == id } } ?: sources.firstOrNull()?.id

    fun activeUrlSourceLocation(sources: List<PlaylistSource>, activeId: String?): String? = sources
        .firstOrNull { it.id == activeId && it.type != PlaylistSourceType.FILE }
        ?.location

    /**
     * Restores [PlaylistOutcome.Loaded.sourceUrl] from the saved [PlaylistSource] when the outcome
     * itself doesn't carry one.
     *
     * A snapshot written by the pre-`sourceUrl` cache format restores with null (see
     * `PlaylistSnapshotCodec.decodeV1`), and that left the playlist with no way to be refreshed at
     * all: Home hides its refresh button when there is no url, and [PlaylistController.refreshPlaylist]
     * had nothing to re-fetch from - so the only way back to a live copy was deleting the source and
     * pasting the url in again. The url was in the saved source the whole time; this just consults it.
     *
     * A FILE source keeps its null, which is the one case where the field genuinely means "there is
     * nothing to re-fetch" rather than "we forgot".
     */
    fun withKnownSourceUrl(outcome: PlaylistOutcome, savedSourceUrl: () -> String?): PlaylistOutcome =
        if (outcome !is PlaylistOutcome.Loaded || outcome.sourceUrl != null) {
            outcome
        } else {
            outcome.copy(sourceUrl = savedSourceUrl())
        }

    /**
     * Looked up by source id rather than a single flat preference, since there can now be several
     * saved sources each with their own name (see PlaylistSource). A brand-new source (added via
     * loadPlaylistFromUrl/loadFromFile/loadXtreamPlaylist) isn't in the list yet at this point -
     * setPlaylistDisplayName patches displayName in directly once it's known, same as it always has.
     */
    fun displayNameFor(outcome: PlaylistOutcome, sources: List<PlaylistSource>): String? =
        (outcome as? PlaylistOutcome.Loaded)?.sourceFingerprint
            ?.let { id -> sources.firstOrNull { it.id == id } }
            // The name the user gave it, or one derived from where it came from. Never the id: that
            // is a SHA-256, and the screens used to print its first eight characters as the title.
            ?.let { source -> source.displayName ?: PlaylistSourceLabel.forLocation(source.type, source.location) }

    /**
     * Keeping the previous source's channels here would label A as B if loading B fails.
     * Refresh uses startUrlLoad and deliberately retains the same source's usable channels.
     */
    fun selectedSourceState(source: PlaylistSource, saveState: PlaylistSourceSaveState): PlaylistUiState =
        PlaylistUiState(
            isLoading = true,
            displayName = source.displayName,
            sourceUrl = source.location.takeIf { source.type != PlaylistSourceType.FILE },
            sourceSaveState = saveState,
        )
}
