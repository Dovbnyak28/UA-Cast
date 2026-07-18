package com.uacastplayer

import android.app.Application
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupFavorite
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupMergePolicy
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.data.cache.CachePaths
import com.uacastplayer.data.cache.CacheSizeUtils
import com.uacastplayer.data.epg.EpgOutcome
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistOutcomeReducer
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.DeviceSpecsProvider
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgSourceAutoDetect
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.icons.LogoUpdateReminder
import com.uacastplayer.log.AppLog
import com.uacastplayer.performance.DevicePerformanceClassifier
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.performance.DeviceTierDefaults
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceAddResult
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceRemovalResult
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.XtreamUrlBuilder
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.CacheSizes
import com.uacastplayer.settings.IconPlaceholdersBannerPolicy
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppViewModel"

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val needsLanguagePicker: Boolean = true,
    val needsTermsAcceptance: Boolean = true,
)

private const val EPG_TICK_MILLIS = 30_000L

/**
 * Root view model for app-wide, cross-screen state (language, playlist/channels, EPG, icons,
 * favorites, settings). Screen-scoped state such as the player stack lives in its own view model
 * instead.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val playlistRepository = PlaylistRepository(application)
    private val epgRepository = EpgRepository(application)
    private val iconRepository = IconRepository(application)
    private val iconPrefetcher = IconPrefetcher(application, iconRepository)
    private val favoritesRepository = FavoritesRepository(application)
    private var unmeteredNetworkWatcher: AutoCloseable? = null

    private val baseDeviceTier: DeviceTier = DeviceSpecsProvider.current(application).let { specs ->
        DevicePerformanceClassifier.classify(specs.totalRamBytes, specs.cpuCoreCount, specs.sdkInt)
    }
    private var playlistChannelCount = 0
    private var epgProgrammeCount = 0

    val castState: StateFlow<CastPlaybackState> = CastSessionRepository.getInstance(application).state
    val favorites: StateFlow<List<FavoriteChannel>> = favoritesRepository.favorites

    // AppPreferences is a plain synchronous wrapper, not itself observable, and the player that
    // writes lastWatchedChannelKey is a separate ViewModel instance - so this needs an explicit
    // refresh (see refreshLastWatchedChannel()) rather than picking up the write automatically.
    private val _lastWatchedChannelKey = MutableStateFlow(preferences.lastWatchedChannelKey)
    val lastWatchedChannelKey: StateFlow<String?> = _lastWatchedChannelKey.asStateFlow()

    /** Called when the player closes, so Home's "continue watching" card reflects whatever was just played. */
    fun refreshLastWatchedChannel() {
        _lastWatchedChannelKey.value = preferences.lastWatchedChannelKey
    }

    private val _uiState = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            appTheme = preferences.appTheme,
            needsLanguagePicker = !preferences.hasChosenLanguage,
            needsTermsAcceptance = !preferences.hasAcceptedTerms,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

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

    private fun setActivePlaylistSourceId(id: String?) {
        preferences.activePlaylistSourceId = id
        _activePlaylistSourceId.value = id
    }

    /** Set by loadPlaylistFromUrl/loadPlaylistFromFile/loadXtreamPlaylist right before starting a
     * load that's meant to become a brand-new saved source; consumed by setPlaylistDisplayName
     * once that load succeeds and the user's typed name (if any) is known. Deliberately NOT set by
     * refreshPlaylist or switchPlaylistSource, which reload an *existing* source instead. */
    private var pendingNewSource: PlaylistSource? = null

    private val _epgState = MutableStateFlow(
        EpgUiState(selectedSource = preferences.epgSource, customUrl = preferences.customEpgUrl)
    )
    val epgState: StateFlow<EpgUiState> = _epgState.asStateFlow()

    private val _iconPrefetchState = MutableStateFlow(
        IconPrefetchUiState(
            wifiOnly = preferences.iconWifiOnly,
            updateReminderDue = LogoUpdateReminder.isDue(preferences.lastIconPrefetchAtMillis, System.currentTimeMillis()),
        )
    )
    val iconPrefetchState: StateFlow<IconPrefetchUiState> = _iconPrefetchState.asStateFlow()

    private val _settingsState = MutableStateFlow(
        SettingsUiState(
            iconDisplayMode = resolvedIconDisplayMode(baseDeviceTier),
            iconDisplayModeIsAutomatic = !preferences.hasChosenIconDisplayMode,
            showIconTierBanner = iconTierBannerVisible(resolvedIconDisplayMode(baseDeviceTier)),
            listDensity = resolvedListDensity(baseDeviceTier),
            channelLayout = preferences.channelLayout,
            bufferSize = preferences.bufferSize,
            favoritesSortOrder = preferences.favoritesSortOrder,
            customIconSources = iconRepository.customIconSources(),
            wrapAroundEnabled = preferences.wrapAroundEnabled,
            autoSkipDeadEnabled = preferences.autoSkipDeadEnabled,
            deviceTier = baseDeviceTier,
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    /** One-shot result of the last successful [importBackupFrom] - the Settings screen shows it
     * (e.g. as a toast) and clears it via [dismissBackupImportSummary]. */
    private val _backupImportSummary = MutableStateFlow<BackupImportSummary?>(null)
    val backupImportSummary: StateFlow<BackupImportSummary?> = _backupImportSummary.asStateFlow()

    private val _showBatteryOptimizationHint = MutableStateFlow(false)
    val showBatteryOptimizationHint: StateFlow<Boolean> = _showBatteryOptimizationHint.asStateFlow()

    init {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val restored = epgRepository.restoreSnapshot()
            if (restored != null) {
                applyEpgOutcome(restored)
            } else {
                // No cached snapshot (fresh install, or cache was cleared) - without this, the
                // configured source is never fetched until the user manually reopens Settings and
                // reselects it, leaving the Home screen's "restoring" status stuck forever.
                _epgState.update { it.copy(isLoading = true) }
                val customUrl = preferences.customEpgUrl
                val outcome = if (customUrl != null) {
                    epgRepository.loadFromUrl(customUrl)
                } else {
                    epgRepository.loadFromSource(preferences.epgSource)
                }
                applyEpgOutcome(outcome)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(EPG_TICK_MILLIS)
                _epgState.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
        }
        viewModelScope.launch {
            castState.map { it.isSessionConnected }.distinctUntilChanged().collect { connected ->
                if (connected) maybeOfferBatteryOptimizationHint()
            }
        }
        refreshCacheSizes()
    }

    /** Shown at most once automatically (see [AppPreferences.hasSeenBatteryOptimizationHint]); manufacturer battery managers (MIUI/HyperOS and friends) otherwise silently kill the cast proxy in the background. */
    private fun maybeOfferBatteryOptimizationHint() {
        if (preferences.hasSeenBatteryOptimizationHint) return
        val powerManager = getApplication<Application>().getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)) return
        _showBatteryOptimizationHint.value = true
    }

    /** Settings screen entry point to bring the hint back up manually, bypassing the "seen" gate. */
    fun reopenBatteryOptimizationHint() {
        _showBatteryOptimizationHint.value = true
    }

    fun dismissBatteryOptimizationHint() {
        preferences.hasSeenBatteryOptimizationHint = true
        _showBatteryOptimizationHint.value = false
    }

    fun selectLanguage(language: AppLanguage) {
        preferences.language = language
        _uiState.value = _uiState.value.copy(language = language, needsLanguagePicker = false)
    }

    fun selectAppTheme(theme: AppTheme) {
        preferences.appTheme = theme
        _uiState.value = _uiState.value.copy(appTheme = theme)
    }

    /** Declining is handled at the Activity level (exits the app) - see [MainActivity]. */
    fun acceptTerms() {
        preferences.hasAcceptedTerms = true
        _uiState.value = _uiState.value.copy(needsTermsAcceptance = false)
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
                viewModelScope.launch { playlistRepository.saveSources(result.sources) }
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
        viewModelScope.launch {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(playlistUrl, extraEpgUrls = listOf(epgUrl)))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        pendingNewSource = newPendingSource(PlaylistSourceType.FILE, uri.toString())
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun newPendingSource(type: PlaylistSourceType, location: String): PlaylistSource = PlaylistSource(
        id = Fingerprint.of(location),
        type = type,
        location = location,
        displayName = null,
        addedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun startUrlLoad(url: String) {
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(url))
        }
    }

    fun selectEpgSource(source: EpgSource) {
        preferences.epgSource = source
        preferences.customEpgUrl = null
        preferences.hasChosenEpgSource = true
        _epgState.update {
            it.copy(selectedSource = source, customUrl = null, suggestedUrl = null, isLoading = true, hasError = false)
        }
        viewModelScope.launch {
            applyEpgOutcome(epgRepository.loadFromSource(source))
        }
    }

    /** Accepts the "EPG address found in playlist" suggestion (see [EpgSourceAutoDetect]) - counts
     * as the user's own manual choice from here on, same as [selectEpgSource]. */
    fun useSuggestedEpgUrl() {
        val url = epgState.value.suggestedUrl ?: return
        applyCustomEpgUrl(url, markChosen = true)
    }

    private fun applyCustomEpgUrl(url: String, markChosen: Boolean) {
        preferences.customEpgUrl = url
        if (markChosen) preferences.hasChosenEpgSource = true
        _epgState.update { it.copy(customUrl = url, suggestedUrl = null, isLoading = true, hasError = false) }
        viewModelScope.launch {
            applyEpgOutcome(epgRepository.loadFromUrl(url))
        }
    }

    /** Only ever called right after an actual playlist (re)load - see [applyPlaylistOutcome]'s
     * `fromCache` gate - so a startup cache restore can't re-trigger this on every launch. */
    private fun handleEpgAutoDetect(epgUrls: List<String>) {
        val currentUrl = epgState.value.customUrl
        when (val action = EpgSourceAutoDetect.decide(epgUrls, preferences.hasChosenEpgSource, currentUrl)) {
            is EpgSourceAutoDetect.Action.Apply -> applyCustomEpgUrl(action.url, markChosen = false)
            is EpgSourceAutoDetect.Action.Suggest -> _epgState.update { it.copy(suggestedUrl = action.url) }
            EpgSourceAutoDetect.Action.Ignore -> Unit
        }
    }

    fun setIconWifiOnly(enabled: Boolean) {
        preferences.iconWifiOnly = enabled
        _iconPrefetchState.update { it.copy(wifiOnly = enabled) }
    }

    suspend fun resolveChannelIcon(channel: M3uChannel): File? {
        if (settingsState.value.iconDisplayMode == IconDisplayMode.PLACEHOLDERS) return null
        val epgIconUrl = epgState.value.data?.index?.match(channel)?.iconUrl
        return iconRepository.resolveIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
    }

    /** For GroupIconCollage - a disk-cache-only lookup, never fetches. See [IconRepository.cachedIconFile]. */
    suspend fun cachedChannelIcon(channel: M3uChannel): File? {
        if (settingsState.value.iconDisplayMode == IconDisplayMode.PLACEHOLDERS) return null
        val epgIconUrl = epgState.value.data?.index?.match(channel)?.iconUrl
        return iconRepository.cachedIconFile(channel.tvgLogo, epgIconUrl, channel.tvgId)
    }

    fun isFavorite(channel: M3uChannel): Boolean = favoritesRepository.isFavorite(channel)

    fun toggleFavorite(channel: M3uChannel) = favoritesRepository.toggleFavorite(channel)

    fun removeFavorite(key: String) = favoritesRepository.remove(key)

    fun reorderFavorites(newOrder: List<FavoriteChannel>) = favoritesRepository.reorder(newOrder)

    fun setIconDisplayMode(mode: IconDisplayMode) {
        preferences.iconDisplayMode = mode
        // Writing the preference above makes hasChosenIconDisplayMode true from here on, so this
        // is no longer a tier default no matter what mode was picked - the banner/caption for it
        // must disappear immediately, not just next time isTierDefault happens to get recomputed.
        _settingsState.update {
            it.copy(iconDisplayMode = mode, iconDisplayModeIsAutomatic = false, showIconTierBanner = false)
        }
    }

    /** One-time dismissal of the Channels-tab tier-default icon banner - see [IconPlaceholdersBannerPolicy]. */
    fun dismissIconTierBanner() {
        preferences.hasSeenIconTierHint = true
        _settingsState.update { it.copy(showIconTierBanner = false) }
    }

    private fun iconTierBannerVisible(mode: IconDisplayMode): Boolean =
        IconPlaceholdersBannerPolicy.shouldShow(
            iconDisplayMode = mode,
            isTierDefault = !preferences.hasChosenIconDisplayMode,
            hasSeenHint = preferences.hasSeenIconTierHint,
        )

    fun setListDensity(density: ListDensity) {
        preferences.listDensity = density
        _settingsState.update { it.copy(listDensity = density) }
    }

    /** [DeviceTierDefaults] only apply until the user picks explicitly; [AppPreferences.hasChosenIconDisplayMode] tracks that. */
    private fun resolvedIconDisplayMode(tier: DeviceTier): IconDisplayMode =
        if (preferences.hasChosenIconDisplayMode) preferences.iconDisplayMode else DeviceTierDefaults.iconDisplayMode(tier)

    private fun resolvedListDensity(tier: DeviceTier): ListDensity =
        if (preferences.hasChosenListDensity) preferences.listDensity else DeviceTierDefaults.listDensity(tier)

    private fun recomputeDeviceTierDefaults() {
        val effectiveTier = DevicePerformanceClassifier.adjustForContentSize(baseDeviceTier, playlistChannelCount, epgProgrammeCount)
        val iconDisplayMode = resolvedIconDisplayMode(effectiveTier)
        _settingsState.update {
            it.copy(
                deviceTier = effectiveTier,
                iconDisplayMode = iconDisplayMode,
                iconDisplayModeIsAutomatic = !preferences.hasChosenIconDisplayMode,
                showIconTierBanner = iconTierBannerVisible(iconDisplayMode),
                listDensity = resolvedListDensity(effectiveTier),
            )
        }
    }

    fun setChannelLayout(layout: ChannelLayout) {
        preferences.channelLayout = layout
        _settingsState.update { it.copy(channelLayout = layout) }
    }

    fun setBufferSize(size: BufferSize) {
        preferences.bufferSize = size
        _settingsState.update { it.copy(bufferSize = size) }
    }

    fun setFavoritesSortOrder(order: FavoritesSortOrder) {
        preferences.favoritesSortOrder = order
        _settingsState.update { it.copy(favoritesSortOrder = order) }
    }

    fun addCustomIconSource(rawUrl: String) {
        val url = rawUrl.trim()
        val isHttpUrl = url.startsWith("http://") || url.startsWith("https://")
        val error = when {
            !isHttpUrl -> IconSourceAddError.INVALID_URL
            url in iconRepository.customIconSources() -> IconSourceAddError.ALREADY_ADDED
            else -> null
        }
        if (error != null) {
            _settingsState.update { it.copy(iconSourceAddError = error) }
            return
        }
        iconRepository.addCustomIconSource(url)
        _settingsState.update {
            it.copy(customIconSources = iconRepository.customIconSources(), iconSourceAddError = null)
        }
    }

    fun removeCustomIconSource(url: String) {
        iconRepository.removeCustomIconSource(url)
        _settingsState.update { it.copy(customIconSources = iconRepository.customIconSources()) }
    }

    fun dismissIconSourceError() {
        _settingsState.update { it.copy(iconSourceAddError = null) }
    }

    fun setWrapAroundEnabled(enabled: Boolean) {
        preferences.wrapAroundEnabled = enabled
        _settingsState.update { it.copy(wrapAroundEnabled = enabled) }
    }

    fun setAutoSkipDeadEnabled(enabled: Boolean) {
        preferences.autoSkipDeadEnabled = enabled
        _settingsState.update { it.copy(autoSkipDeadEnabled = enabled) }
    }

    /** Writes the current playlist sources/favorites/settings as JSON to a SAF-picked [uri] - see
     * [BackupCodec]. Deliberately excludes caches/snapshots (see the Data-section string) - those
     * are re-derivable from the sources themselves and would just bloat the file. */
    fun exportBackupTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = BackupCodec.encode(buildBackupData())
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onFailure { e -> AppLog.w(TAG) { "Backup export failed: ${e.javaClass.simpleName}" } }
        }
    }

    /** Reads a SAF-picked [uri] and merges its sources/favorites into what's already saved (see
     * [BackupMergePolicy]), then applies its settings through the same setters a manual change in
     * Settings would use. A no-op (nothing happens, no summary shown) for an unreadable file, an
     * empty one, or one with an unrecognized [BackupCodec] version. */
    fun importBackupFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }
                    .onFailure { e -> AppLog.w(TAG) { "Backup import read failed: ${e.javaClass.simpleName}" } }
                    .getOrNull()
            } ?: return@launch
            val data = BackupCodec.decode(text) ?: return@launch

            val mergeResult = BackupMergePolicy.merge(
                existingSources = _playlistSources.value,
                existingFavorites = favorites.value,
                importedSources = data.sources,
                importedFavorites = data.favorites,
            )
            _playlistSources.value = mergeResult.sources
            playlistRepository.saveSources(mergeResult.sources)
            // "reorder" also just means "replace wholesale + persist" - there's no dedicated
            // bulk-set method on FavoritesRepository, and this does exactly what's needed here.
            favoritesRepository.reorder(mergeResult.favorites)
            applyImportedSettings(data.settings)

            _backupImportSummary.value = BackupImportSummary(
                mergeResult.importedSourceCount,
                mergeResult.importedFavoriteCount,
            )
        }
    }

    fun dismissBackupImportSummary() {
        _backupImportSummary.value = null
    }

    private fun buildBackupData(): BackupData {
        val sources = _playlistSources.value.map {
            BackupPlaylistSource(it.id, it.type.name, it.location, it.displayName, it.addedAtEpochMillis)
        }
        val favoritesBackup = favorites.value.map {
            BackupFavorite(it.key, it.displayName, it.streamUrl, it.tvgId, it.groupTitle, it.addedAtMillis)
        }
        val exportEpgSourceId = if (preferences.hasChosenEpgSource && preferences.customEpgUrl == null) {
            preferences.epgSource.id
        } else {
            null
        }
        val settings = BackupSettings(
            iconDisplayMode = settingsState.value.iconDisplayMode.name,
            listDensity = settingsState.value.listDensity.name,
            bufferSize = settingsState.value.bufferSize.name,
            epgSourceId = exportEpgSourceId,
            epgCustomUrl = if (preferences.hasChosenEpgSource) preferences.customEpgUrl else null,
        )
        return BackupData(sources, favoritesBackup, settings)
    }

    /** Reuses the same setters a manual change in Settings would call, so importing settings can't
     * drift from what those paths already do (persisting to [preferences], updating state, and for
     * EPG, actually reloading). */
    private fun applyImportedSettings(settings: BackupSettings) {
        settings.iconDisplayMode
            ?.let { name -> runCatching { IconDisplayMode.valueOf(name) }.getOrNull() }
            ?.let(::setIconDisplayMode)
        settings.listDensity
            ?.let { name -> runCatching { ListDensity.valueOf(name) }.getOrNull() }
            ?.let(::setListDensity)
        settings.bufferSize
            ?.let { name -> runCatching { BufferSize.valueOf(name) }.getOrNull() }
            ?.let(::setBufferSize)
        when {
            settings.epgCustomUrl != null -> applyCustomEpgUrl(settings.epgCustomUrl, markChosen = true)
            settings.epgSourceId != null ->
                EpgSource.entries.firstOrNull { it.id == settings.epgSourceId }?.let(::selectEpgSource)
            else -> Unit
        }
    }

    fun clearCache(kind: CacheKind) {
        val filesDir = getApplication<Application>().filesDir
        val file = when (kind) {
            CacheKind.PLAYLIST -> File(filesDir, CachePaths.PLAYLIST_SNAPSHOT)
            CacheKind.EPG -> File(filesDir, CachePaths.EPG_SNAPSHOT)
            CacheKind.ICONS -> File(filesDir, CachePaths.ICON_CACHE_DIR)
            CacheKind.COIL -> File(filesDir, CachePaths.COIL_CACHE_DIR)
        }
        // Signalled here (synchronously, before launching) so a prefetch tick that's about to
        // start doesn't slip in between this call and the delete below.
        if (kind == CacheKind.ICONS) {
            iconPrefetchJob?.cancel()
            _iconPrefetchState.update { it.copy(isRunning = false) }
        }
        viewModelScope.launch {
            // Cancellation is cooperative, not instant - join() waits for the prefetch coroutine
            // to actually unwind so it can't still be mid-write into the directory this deletes.
            if (kind == CacheKind.ICONS) iconPrefetchJob?.join()
            withContext(Dispatchers.IO) { CacheSizeUtils.clear(file) }
            // The deleted files are exactly what resolveChannelIcon's in-memory cache may still be
            // holding onto (positive results pointing at now-gone files, or negative results that
            // should be retried once the icon cache is empty) - drop it so the next resolve
            // actually looks at disk again instead of trusting stale in-memory entries.
            if (kind == CacheKind.ICONS) iconRepository.invalidateMemoryCache()
            refreshCacheSizes()
        }
    }

    private fun refreshCacheSizes() {
        viewModelScope.launch {
            val filesDir = getApplication<Application>().filesDir
            val sizes = withContext(Dispatchers.IO) {
                CacheSizes(
                    playlistBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.PLAYLIST_SNAPSHOT)),
                    epgBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.EPG_SNAPSHOT)),
                    iconCacheBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.ICON_CACHE_DIR)),
                    coilCacheBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.COIL_CACHE_DIR)),
                )
            }
            _settingsState.update { it.copy(cacheSizes = sizes) }
        }
    }

    private var lastPrefetchChannels: List<M3uChannel> = emptyList()
    private var iconPrefetchJob: Job? = null

    private fun triggerIconPrefetch(channels: List<M3uChannel>) {
        lastPrefetchChannels = channels
        unmeteredNetworkWatcher?.close()
        unmeteredNetworkWatcher = iconPrefetcher.awaitUnmeteredNetwork {
            iconPrefetchJob?.cancel()
            iconPrefetchJob = viewModelScope.launch { runIconPrefetch(lastPrefetchChannels) }
        }
        iconPrefetchJob?.cancel()
        iconPrefetchJob = viewModelScope.launch { runIconPrefetch(channels) }
    }

    private suspend fun runIconPrefetch(channels: List<M3uChannel>) {
        if (channels.isEmpty()) return
        if (settingsState.value.iconDisplayMode != IconDisplayMode.CACHE) return
        _iconPrefetchState.update { it.copy(isRunning = true, completed = 0, total = channels.size) }
        iconPrefetcher.prefetch(channels, preferences.iconWifiOnly) { progress ->
            _iconPrefetchState.update { it.copy(completed = progress.completed, total = progress.total) }
        }
        preferences.lastIconPrefetchAtMillis = System.currentTimeMillis()
        _iconPrefetchState.update {
            it.copy(isRunning = false, updateReminderDue = false, completedRuns = it.completedRuns + 1)
        }
        // Prefetch may have just written icon files for channels that previously cached a negative
        // (no-icon) memory result - drop the cache so those channels get re-resolved from disk.
        // completedRuns above is the signal ChannelIcon's refreshKey uses to actually re-render.
        iconRepository.invalidateMemoryCache()
        refreshCacheSizes()
    }

    override fun onCleared() {
        unmeteredNetworkWatcher?.close()
        super.onCleared()
    }

    private fun applyPlaylistOutcome(outcome: PlaylistOutcome, fromCache: Boolean = false) {
        if (outcome is PlaylistOutcome.Loaded) {
            val channels = outcome.groups.flatMap { it.channels }
            playlistChannelCount = channels.size
            recomputeDeviceTierDefaults()
            triggerIconPrefetch(channels)
            // Only on an actual load, never a startup cache restore - see handleEpgAutoDetect's doc.
            if (!fromCache) handleEpgAutoDetect(outcome.epgUrls)
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
        refreshCacheSizes()
    }

    private fun applyEpgOutcome(outcome: EpgOutcome) {
        _epgState.update { current ->
            when (outcome) {
                is EpgOutcome.Loaded -> current.copy(data = outcome.data, isLoading = false, hasError = false)
                else -> current.copy(isLoading = false, hasError = true)
            }
        }
        if (outcome is EpgOutcome.Loaded) {
            epgProgrammeCount = outcome.data.programmesByChannelId.values.sumOf { it.size }
            recomputeDeviceTierDefaults()
        }
        refreshCacheSizes()
    }
}
