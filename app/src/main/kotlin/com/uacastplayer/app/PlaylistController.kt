package com.uacastplayer.app

import android.net.Uri
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistOutcomeReducer
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceAddResult
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceRemovalResult
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.XtreamUrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns every saved [PlaylistSource] plus the currently active playlist's load state - moved out of
 * [com.uacastplayer.AppViewModel] as a move-only split (see B1 in the consolidated fix plan);
 * behavior is unchanged, this is still thin impure glue over [PlaylistRepository].
 *
 * [onLoaded] and [onStateChanged] are AppViewModel's hooks into cross-controller concerns
 * (device-tier recompute, icon prefetch, EPG auto-detect, cache-size refresh) that don't belong to
 * playlist state itself.
 */
class PlaylistController(
    private val preferences: AppPreferences,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val onLoaded: (
        channels: List<M3uChannel>,
        groups: List<GroupedChannels>,
        epgUrls: List<String>,
        fromCache: Boolean,
    ) -> Unit,
    private val onStateChanged: () -> Unit,
) {
    private val _playlistState = MutableStateFlow(PlaylistUiState())
    val playlistState: StateFlow<PlaylistUiState> = _playlistState.asStateFlow()

    /** Every saved playlist source (see [PlaylistSource]), for Home's source-switcher bottom
     * sheet - [playlistState] only ever reflects the one currently active. */
    private val _playlistSources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val playlistSources: StateFlow<List<PlaylistSource>> = _playlistSources.asStateFlow()

    /** Mirrors [AppPreferences.activePlaylistSourceId] as a StateFlow - NOT the same as
     * [PlaylistUiState.activePlaylistId], which is truncated for display; the bottom sheet needs
     * the full, untruncated id to match against [PlaylistSource.id]. */
    private val _activePlaylistSourceId = MutableStateFlow(preferences.activePlaylistSourceId)
    val activePlaylistSourceId: StateFlow<String?> = _activePlaylistSourceId.asStateFlow()

    /** Set by loadPlaylistFromUrl/loadPlaylistFromFile/loadXtreamPlaylist right before starting a
     * load that's meant to become a brand-new saved source; consumed by setPlaylistDisplayName
     * once that load succeeds and the user's typed name (if any) is known. Deliberately NOT set by
     * refreshPlaylist or switchPlaylistSource, which reload an *existing* source instead. */
    private var pendingNewSource: PlaylistSource? = null

    /**
     * The one load allowed to be in flight. Every entry point that loads a playlist goes through
     * [launchLoad], which cancels whatever this holds first: switching sources twice in quick
     * succession used to leave two loads racing, and the one that happened to finish *last* won
     * [_playlistState] - not the one the user picked last. Saves and deletes are deliberately NOT
     * routed through here; cancelling persistence would lose data rather than a stale result.
     */
    private var loadJob: Job? = null

    var channelCount: Int = 0
        private set

    private fun launchLoad(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = scope.launch { block() }
    }

    /** Restores the active source's cached snapshot at startup, migrating a pre-multi-playlist
     * legacy snapshot into the sources list first if needed. Called once from AppViewModel.init. */
    fun loadInitialSource() {
        launchLoad {
            var sources = playlistRepository.loadSources()
            if (sources.isEmpty()) {
                // Upgrading from before multi-playlist support (or a fresh install with nothing
                // loaded yet) - see PlaylistRepository.migrateLegacySnapshotIfNeeded.
                val migrated = playlistRepository.migrateLegacySnapshotIfNeeded()
                if (migrated != null) {
                    sources = listOf(migrated.copy(displayName = preferences.playlistDisplayName))
                    playlistRepository.saveSources(sources)
                    setActivePlaylistSourceId(migrated.id)
                }
            }
            _playlistSources.value = sources
            val activeId = preferences.activePlaylistSourceId ?: sources.firstOrNull()?.id
            if (activeId != null) {
                setActivePlaylistSourceId(activeId)
                playlistRepository.restoreSnapshot(activeId)?.let { applyPlaylistOutcome(it, fromCache = true) }
            }
        }
    }

    fun setActivePlaylistSourceId(id: String?) {
        preferences.activePlaylistSourceId = id
        _activePlaylistSourceId.value = id
    }

    /**
     * Only meaningful right after a [pendingNewSource] load - persists the user's typed name (or
     * an Xtream source's server-host default) into the sources list and marks it active. A no-op
     * if nothing is pending (e.g. called after a plain [refreshPlaylist]/[switchPlaylistSource],
     * neither of which set one).
     */
    fun setPlaylistDisplayName(name: String) {
        val pending = pendingNewSource ?: return
        pendingNewSource = null
        val effectiveName = name.ifBlank {
            if (pending.type == PlaylistSourceType.XTREAM) XtreamUrlBuilder.serverHost(pending.location) else null
        }
        val newSource = pending.copy(displayName = effectiveName)
        when (val result = PlaylistSourcePolicy.add(_playlistSources.value, newSource)) {
            is PlaylistSourceAddResult.Added -> {
                _playlistSources.value = result.sources
                setActivePlaylistSourceId(newSource.id)
                _playlistState.value = _playlistState.value.copy(displayName = newSource.displayName)
                scope.launch { playlistRepository.saveSources(result.sources) }
            }
            // Already loaded and shown - it just won't be remembered as a saved source, so
            // switching away would lose it. Rare (10 sources is a lot) and no dedicated UI for it
            // yet; see the Block 3 changelog note.
            PlaylistSourceAddResult.LimitReached -> Unit
        }
    }

    fun loadPlaylistFromUrl(url: String) {
        if (url.isBlank()) return
        val trimmed = url.trim()
        pendingNewSource = newPendingSource(PlaylistSourceType.URL, trimmed)
        startUrlLoad(trimmed)
    }

    /** Xtream Codes source: server/username/password are turned into a plain M3U URL (see
     * XtreamUrlBuilder) and go through the exact same loading pipeline from there - no separate
     * credential storage, the resulting URL is saved the same way any other URL-sourced playlist
     * is. The panel's XMLTV endpoint is passed along as a found EPG URL too (see
     * EpgSourceAutoDetect), on top of whatever the M3U's own #EXTM3U header might advertise.
     */
    fun loadXtreamPlaylist(server: String, username: String, password: String) {
        if (server.isBlank() || username.isBlank() || password.isBlank()) return
        val playlistUrl = XtreamUrlBuilder.playlistUrl(server, username, password)
        val epgUrl = XtreamUrlBuilder.epgUrl(server, username, password)
        pendingNewSource = newPendingSource(PlaylistSourceType.XTREAM, playlistUrl)
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        launchLoad {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(playlistUrl, extraEpgUrls = listOf(epgUrl)))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        pendingNewSource = newPendingSource(PlaylistSourceType.FILE, uri.toString())
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        launchLoad {
            applyPlaylistOutcome(playlistRepository.loadFromFile(uri))
        }
    }

    /** Re-downloads the active playlist from its saved URL - a no-op if it came from a file
     * import (nothing to re-fetch) or nothing has loaded yet. Deliberately doesn't touch
     * [pendingNewSource]: this reloads the *existing* active source, it doesn't add a new one. */
    fun refreshPlaylist() {
        playlistState.value.sourceUrl?.let(::startUrlLoad)
    }

    /** Switches to an already-saved source (see Home's source-switcher bottom sheet) - shows its
     * cached snapshot instantly when one exists instead of always re-fetching over the network. */
    fun switchPlaylistSource(source: PlaylistSource) {
        setActivePlaylistSourceId(source.id)
        _playlistState.value = _playlistState.value.copy(
            isLoading = true,
            error = null,
            displayName = source.displayName,
        )
        launchLoad {
            val cached = playlistRepository.restoreSnapshot(source.id)
            if (cached != null) {
                applyPlaylistOutcome(cached, fromCache = true)
            } else {
                val outcome = if (source.type == PlaylistSourceType.FILE) {
                    playlistRepository.loadFromFile(Uri.parse(source.location))
                } else {
                    playlistRepository.loadFromUrl(source.location)
                }
                applyPlaylistOutcome(outcome)
            }
        }
    }

    /** Removes a saved source (see Home's source-switcher bottom sheet). Removing the active one
     * falls back to the most recently added remaining source, or clears the screen entirely if it
     * was the last source left - see [PlaylistSourcePolicy.remove]. */
    fun removePlaylistSource(id: String) {
        val previousActiveId = preferences.activePlaylistSourceId
        val result = PlaylistSourcePolicy.remove(_playlistSources.value, previousActiveId, id)
        if (result !is PlaylistSourceRemovalResult.Removed) return
        _playlistSources.value = result.sources
        scope.launch {
            playlistRepository.saveSources(result.sources)
            playlistRepository.deleteSnapshot(id)
        }
        setActivePlaylistSourceId(result.newActiveId)
        when {
            result.newActiveId == null -> _playlistState.value = PlaylistUiState()
            // The removed source was the active one and a different source took over - load it.
            result.newActiveId != previousActiveId ->
                result.sources.firstOrNull { it.id == result.newActiveId }?.let(::switchPlaylistSource)
            else -> Unit // Removed a source that wasn't active - nothing else to reload.
        }
    }

    /** Wholesale-replaces the saved sources list from a backup import merge (see
     * [com.uacastplayer.backup.BackupMergePolicy]) and persists it - the merged list is already
     * computed by the caller, this just applies and saves it. */
    fun applyImportedSources(sources: List<PlaylistSource>) {
        _playlistSources.value = sources
        scope.launch { playlistRepository.saveSources(sources) }
    }

    private fun newPendingSource(type: PlaylistSourceType, location: String): PlaylistSource = PlaylistSource(
        id = Fingerprint.of(location),
        type = type,
        location = location,
        displayName = null,
        addedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun startUrlLoad(url: String) {
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        launchLoad {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(url))
        }
    }

    private suspend fun applyPlaylistOutcome(outcome: PlaylistOutcome, fromCache: Boolean = false) {
        if (outcome is PlaylistOutcome.Loaded) {
            // Off the main thread: `scope` is the ViewModel's (Dispatchers.Main.immediate), and
            // this flattens every group of a playlist that routinely runs to tens of thousands of
            // channels - an allocation that size does not belong in the frame that applies a load.
            val channels = withContext(Dispatchers.Default) { outcome.groups.flatMap { it.channels } }
            channelCount = channels.size
            onLoaded(channels, outcome.groups, outcome.epgUrls, fromCache)
        }
        // Looked up by source id rather than a single flat preference, since there can now be
        // several saved sources each with their own name (see PlaylistSource). A brand-new source
        // (added via loadPlaylistFromUrl/loadFromFile/loadXtreamPlaylist) isn't in the list yet at
        // this point - setPlaylistDisplayName patches displayName in directly once it's known,
        // same as it always has.
        val displayName = (outcome as? PlaylistOutcome.Loaded)?.sourceFingerprint
            ?.let { id -> _playlistSources.value.firstOrNull { it.id == id }?.displayName }
        _playlistState.value = PlaylistOutcomeReducer.reduce(
            current = _playlistState.value,
            outcome = outcome,
            fromCache = fromCache,
            displayName = displayName,
        )
        onStateChanged()
    }
}
