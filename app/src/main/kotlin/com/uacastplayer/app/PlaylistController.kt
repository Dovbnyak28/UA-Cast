package com.uacastplayer.app

import android.net.Uri
import androidx.core.net.toUri
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistOutcomeReducer
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceAddResult
import com.uacastplayer.playlist.PlaylistSourceLabel
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceRemovalResult
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.XtreamUrlBuilder
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PlaylistController"

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
    private val loadGeneration = AtomicLong()

    /**
     * Source-list writes can overlap because [PlaylistRepository.saveSources] moves to IO. A user
     * removing several sources quickly used to launch one save per tap, and whichever IO task
     * happened to finish last won on disk - even when it represented an older list. The mutex
     * keeps the physical writes exclusive, while the generation lets superseded waiters skip
     * their stale write. A write already in progress may finish, but the newest generation always
     * follows it and becomes the final durable state.
     */
    val sourcePersistence = PlaylistSourcePersistence(
        scope, playlistRepository, { _playlistSources.value }, ::publishSourceSave,
    )

    var channelCount: Int = 0
        private set

    private fun launchLoad(kind: String, block: suspend () -> Unit) {
        val generation = loadGeneration.incrementAndGet()
        loadJob?.cancel()
        loadJob = scope.launch {
            AppLog.d(TAG) { "Playlist load $generation ($kind) started" }
            try {
                block()
                AppLog.d(TAG) { "Playlist load $generation ($kind) completed" }
            } catch (cancelled: CancellationException) {
                AppLog.d(TAG) { "Playlist load $generation ($kind) cancelled" }
                throw cancelled
            } catch (failure: IOException) {
                AppLog.w(TAG) { "Playlist persistence failed: ${failure.javaClass.simpleName}" }
                applyPlaylistOutcome(PlaylistOutcome.StorageError)
            }
        }
    }

    /** Restores the active source's cached snapshot at startup, migrating a pre-multi-playlist
     * legacy snapshot into the sources list first if needed. Called once from AppViewModel.init. */
    fun loadInitialSource() {
        launchLoad("initial") {
            var sources = playlistRepository.loadSources()
            if (sources.isEmpty()) {
                // Upgrading from before multi-playlist support (or a fresh install with nothing
                // loaded yet) - see PlaylistRepository.migrateLegacySnapshotIfNeeded.
                val migrated = playlistRepository.migrateLegacySnapshotIfNeeded()
                if (migrated != null) {
                    sources = listOf(migrated.copy(displayName = preferences.playlistDisplayName))
                    val sourcesSaved = playlistRepository.saveSources(sources)
                    setActivePlaylistSourceId(migrated.id)
                    // Only now, and deliberately last: until the source list naming the migrated
                    // snapshot is on disk, the legacy file is the only record that the playlist
                    // exists. Dying between the two used to lose it (see
                    // PlaylistRepository.migrateLegacySnapshotIfNeeded); dying after this line
                    // loses nothing, because everything it pointed at has already been written.
                    if (sourcesSaved) playlistRepository.discardLegacySnapshot()
                }
            }
            _playlistSources.value = sources
            val activeId = preferences.activePlaylistSourceId
                ?.takeIf { preferredId -> sources.any { source -> source.id == preferredId } }
                ?: sources.firstOrNull()?.id
            if (activeId != null) {
                val source = sources.first { it.id == activeId }
                selectSource(source)
                loadSavedSource(source)
            }
        }
    }

    private fun setActivePlaylistSourceId(id: String?) {
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
        val pending = pendingNewSource ?: run { sourcePersistence.retry(); return }
        if (!_playlistState.value.sourceReadyToSave) return
        pendingNewSource = null
        _playlistState.value = _playlistState.value.copy(sourceReadyToSave = false)
        // What the user typed, else what the source already knows about itself (a picked file
        // carries its own name - see loadPlaylistFromFile), else one derived from where it came
        // from. The last step used to be an Xtream-only special case; PlaylistSourceLabel answers
        // for every type, which is what stops a URL playlist falling through to its own SHA-256.
        val effectiveName = name.ifBlank { null }
            ?: pending.displayName
            ?: PlaylistSourceLabel.forLocation(pending.type, pending.location)
        val newSource = pending.copy(displayName = effectiveName)
        when (val result = PlaylistSourcePolicy.add(_playlistSources.value, newSource)) {
            is PlaylistSourceAddResult.Added -> {
                _playlistSources.value = result.sources
                setActivePlaylistSourceId(newSource.id)
                _playlistState.value = _playlistState.value.copy(displayName = newSource.displayName)
                persistSources(result.sources)
            }
            PlaylistSourceAddResult.LimitReached -> publishSourceSave(PlaylistSourceSaveState.LIMIT_REACHED)
        }
    }

    fun loadPlaylistFromUrl(url: String) {
        if (url.isBlank()) return
        val trimmed = url.trim()
        if (!beginSourceAdd(newPendingSource(PlaylistSourceType.URL, trimmed))) return
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
        if (!beginSourceAdd(newPendingSource(PlaylistSourceType.XTREAM, playlistUrl))) return
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        launchLoad("xtream") {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(playlistUrl, extraEpgUrls = listOf(epgUrl)))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        // Asked while the grant from the picker is still current - a saved source outlives it, and
        // the provider will not answer later. Without this the only label a file playlist ever had
        // was its own SHA-256 (see PlaylistSourceLabel).
        val pending = newPendingSource(PlaylistSourceType.FILE, uri.toString())
        if (!beginSourceAdd(pending)) return
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        launchLoad("file") {
            val name = playlistRepository.documentName(uri)
            if (pendingNewSource === pending) pendingNewSource = pending.copy(displayName = name)
            applyPlaylistOutcome(playlistRepository.loadFromFile(uri))
        }
    }

    /**
     * Abandons only a brand-new source being loaded by the add-playlist flow.
     *
     * The screen may be left with Back while its network request is running. Letting that request
     * finish applies channels, but the screen that calls [setPlaylistDisplayName] is already gone,
     * so no source ever names the resulting snapshot. Cancelling the job stops state application;
     * deleting through the repository's mutation coordinator also wins over an AtomicFile write
     * that was already too far into IO to observe cancellation.
     */
    fun cancelPendingSourceAdd() {
        val pending = pendingNewSource ?: return
        pendingNewSource = null
        loadJob?.cancel()
        loadJob = null
        _playlistState.value = _playlistState.value.copy(isLoading = false, sourceReadyToSave = false)
        if (_playlistSources.value.none { it.id == pending.id }) {
            scope.launch { playlistRepository.deleteSnapshot(pending.id) }
        }
    }

    /** Re-downloads the active playlist from its saved URL - a no-op if it came from a file
     * import (nothing to re-fetch) or nothing has loaded yet. Deliberately doesn't touch
     * [pendingNewSource]: this reloads the *existing* active source, it doesn't add a new one.
     *
     * Falls back to the saved source's own location for the same reason as [withKnownSourceUrl] -
     * so a playlist restored from an old cache snapshot is still refreshable. */
    fun refreshPlaylist() {
        (playlistState.value.sourceUrl ?: activeUrlSourceLocation())?.let(::startUrlLoad)
    }

    /** Switches to an already-saved source (see Home's source-switcher bottom sheet) - shows its
     * cached snapshot instantly when one exists instead of always re-fetching over the network. */
    fun switchPlaylistSource(source: PlaylistSource) {
        selectSource(source)
        launchLoad("switch") { loadSavedSource(source) }
    }

    private fun selectSource(source: PlaylistSource) {
        pendingNewSource = null
        setActivePlaylistSourceId(source.id)
        channelCount = 0
        // Keeping the previous source's channels here would label A as B if loading B fails.
        // Refresh uses startUrlLoad and deliberately retains the same source's usable channels.
        _playlistState.value = PlaylistUiState(
            isLoading = true,
            displayName = source.displayName,
            sourceUrl = source.location.takeIf { source.type != PlaylistSourceType.FILE },
            sourceSaveState = _playlistState.value.sourceSaveState,
        )
    }

    private suspend fun loadSavedSource(source: PlaylistSource) {
        val cached = playlistRepository.restoreSnapshot(source.id)
        if (cached != null) {
            applyPlaylistOutcome(cached, fromCache = true)
        } else {
            val outcome = if (source.type == PlaylistSourceType.FILE) {
                playlistRepository.loadFromFile(source.location.toUri())
            } else {
                playlistRepository.loadFromUrl(source.location)
            }
            applyPlaylistOutcome(outcome)
        }
    }

    /** Removes a saved source (see Home's source-switcher bottom sheet). Removing the active one
     * falls back to the most recently added remaining source, or clears the screen entirely if it
     * was the last source left - see [PlaylistSourcePolicy.remove]. */
    fun removePlaylistSource(id: String) {
        val previousActiveId = preferences.activePlaylistSourceId
        val result = PlaylistSourcePolicy.remove(_playlistSources.value, previousActiveId, id)
        if (result !is PlaylistSourceRemovalResult.Removed) return
        // A load for the source being removed must not outlive it. It writes a snapshot when it
        // finishes (PlaylistRepository.persistIfLoaded), so a load still in flight would put the
        // deleted playlist back on screen and re-create the file deleted below - orphaned this
        // time, since no source names it any more, so nothing will ever delete it again.
        //
        // Only the active source can be the one loading, so removing any other must leave that load
        // alone. Cancelled rather than awaited: a load stuck in a socket read does not stop until
        // its timeout, and holding the save behind that would risk losing the removal itself if the
        // process died meanwhile - a worse failure than the one being fixed. The branch below that
        // switches to another source cancels this again through launchLoad, harmlessly.
        if (previousActiveId == id) loadJob?.cancel()
        _playlistSources.value = result.sources
        sourcePersistence.markForDeletion(id)
        setActivePlaylistSourceId(result.newActiveId)
        when {
            result.newActiveId == null -> {
                channelCount = 0
                _playlistState.value = PlaylistUiState()
            }
            // The removed source was the active one and a different source took over - load it.
            result.newActiveId != previousActiveId ->
                result.sources.firstOrNull { it.id == result.newActiveId }?.let(::switchPlaylistSource)
            else -> Unit // Removed a source that wasn't active - nothing else to reload.
        }
        persistSources(result.sources)
    }

    /** Wholesale-replaces the saved sources list from a backup import merge (see
     * [com.uacastplayer.backup.BackupMergePolicy]) and persists it - the merged list is already
     * computed by the caller, this just applies and saves it. The returned job lets callers that
     * require a durable boundary (notably process-restarting instrumentation) wait for the write. */
    fun applyImportedSources(sources: List<PlaylistSource>, activateIfNeeded: Boolean = false): Job {
        _playlistSources.value = sources
        if (sources.isEmpty()) {
            loadJob?.cancel()
            pendingNewSource = null
            channelCount = 0
            setActivePlaylistSourceId(null)
            _playlistState.value = PlaylistUiState()
        } else if (activateIfNeeded && !_playlistState.value.hasChannels) {
            val source = sources.firstOrNull { it.id == _activePlaylistSourceId.value } ?: sources.first()
            switchPlaylistSource(source)
        }
        return persistSources(sources)
    }

    private val persistSources: (List<PlaylistSource>) -> Job = sourcePersistence::write

    suspend fun awaitSourcesPersistence(): Boolean {
        return sourcePersistence.awaitPersistence()
    }

    private fun publishSourceSave(state: PlaylistSourceSaveState) {
        _playlistState.value = _playlistState.value.copy(sourceSaveState = state)
    }

    /** Check capacity before download, but allow updating an existing source at the limit. */
    private fun beginSourceAdd(source: PlaylistSource): Boolean {
        if (PlaylistSourcePolicy.add(_playlistSources.value, source) == PlaylistSourceAddResult.LimitReached) {
            publishSourceSave(PlaylistSourceSaveState.LIMIT_REACHED)
            return false
        }
        pendingNewSource = source
        _playlistState.value = _playlistState.value.copy(sourceReadyToSave = false)
        if (_playlistState.value.sourceSaveState != PlaylistSourceSaveState.FAILED) {
            publishSourceSave(PlaylistSourceSaveState.IDLE)
        }
        return true
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
        launchLoad("url") {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(url))
        }
    }

    /**
     * Restores [PlaylistOutcome.Loaded.sourceUrl] from the saved [PlaylistSource] when the outcome
     * itself doesn't carry one.
     *
     * A snapshot written by the pre-`sourceUrl` cache format restores with null (see
     * `PlaylistSnapshotCodec.decodeV1`), and that left the playlist with no way to be refreshed at
     * all: Home hides its refresh button when there is no url, and [refreshPlaylist] had nothing to
     * re-fetch from - so the only way back to a live copy was deleting the source and pasting the
     * url in again. The url was in the saved source the whole time; this just consults it.
     *
     * A FILE source keeps its null, which is the one case where the field genuinely means "there is
     * nothing to re-fetch" rather than "we forgot".
     */
    private fun PlaylistOutcome.withKnownSourceUrl(): PlaylistOutcome =
        if (this !is PlaylistOutcome.Loaded || sourceUrl != null) {
            this
        } else {
            copy(sourceUrl = activeUrlSourceLocation())
        }

    private fun activeUrlSourceLocation(): String? = _playlistSources.value
        .firstOrNull { it.id == _activePlaylistSourceId.value && it.type != PlaylistSourceType.FILE }
        ?.location

    private suspend fun applyPlaylistOutcome(rawOutcome: PlaylistOutcome, fromCache: Boolean = false) {
        val outcome = rawOutcome.withKnownSourceUrl()
        val loadedChannels = if (outcome is PlaylistOutcome.Loaded) {
            // Off the main thread: `scope` is the ViewModel's (Dispatchers.Main.immediate), and
            // this flattens every group of a playlist that routinely runs to tens of thousands of
            // channels - an allocation that size does not belong in the frame that applies a load.
            // The resulting list is also stored in PlaylistUiState and reused by every screen.
            withPlaylistCpu { outcome.groups.flatMap { it.channels } }
        } else {
            null
        }
        // Looked up by source id rather than a single flat preference, since there can now be
        // several saved sources each with their own name (see PlaylistSource). A brand-new source
        // (added via loadPlaylistFromUrl/loadFromFile/loadXtreamPlaylist) isn't in the list yet at
        // this point - setPlaylistDisplayName patches displayName in directly once it's known,
        // same as it always has.
        val displayName = (outcome as? PlaylistOutcome.Loaded)?.sourceFingerprint
            ?.let { id -> _playlistSources.value.firstOrNull { it.id == id } }
            // The name the user gave it, or one derived from where it came from. Never the id: that
            // is a SHA-256, and the screens used to print its first eight characters as the title.
            ?.let { source -> source.displayName ?: PlaylistSourceLabel.forLocation(source.type, source.location) }
        val nextState = PlaylistOutcomeReducer.reduce(
            current = _playlistState.value,
            outcome = outcome,
            fromCache = fromCache,
            displayName = displayName,
            loadedChannels = loadedChannels,
        )
        if (outcome is PlaylistOutcome.Loaded && !loadedChannels.isNullOrEmpty()) {
            val channels = checkNotNull(loadedChannels)
            channelCount = channels.size
            onLoaded(channels, outcome.groups, outcome.epgUrls, fromCache)
        }
        _playlistState.value = nextState.copy(
            sourceReadyToSave = pendingNewSource != null && nextState.error == null && nextState.hasChannels,
        )
        onStateChanged()
    }
}
