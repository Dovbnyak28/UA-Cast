package com.uacastplayer

import android.app.Application
import android.net.Uri
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.cache.CachePaths
import com.uacastplayer.data.cache.CacheSizeUtils
import com.uacastplayer.data.epg.EpgOutcome
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.DeviceSpecsProvider
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.icons.LogoUpdateReminder
import com.uacastplayer.performance.DevicePerformanceClassifier
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.performance.DeviceTierDefaults
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.CacheSizes
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.settings.SettingsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
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

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val needsLanguagePicker: Boolean = true,
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

    private val _uiState = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            needsLanguagePicker = !preferences.hasChosenLanguage,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playlistState = MutableStateFlow(PlaylistUiState())
    val playlistState: StateFlow<PlaylistUiState> = _playlistState.asStateFlow()

    private val _epgState = MutableStateFlow(EpgUiState(selectedSource = preferences.epgSource))
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
            listDensity = resolvedListDensity(baseDeviceTier),
            channelLayout = preferences.channelLayout,
            favoritesSortOrder = preferences.favoritesSortOrder,
            customIconSources = iconRepository.customIconSources(),
            wrapAroundEnabled = preferences.wrapAroundEnabled,
            autoSkipDeadEnabled = preferences.autoSkipDeadEnabled,
            deviceTier = baseDeviceTier,
        )
    )
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _showBatteryOptimizationHint = MutableStateFlow(false)
    val showBatteryOptimizationHint: StateFlow<Boolean> = _showBatteryOptimizationHint.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.restoreSnapshot()?.let { applyPlaylistOutcome(it) }
        }
        viewModelScope.launch {
            epgRepository.restoreSnapshot()?.let { applyEpgOutcome(it) }
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
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
        _uiState.value = _uiState.value.copy(language = language, needsLanguagePicker = false)
    }

    fun loadPlaylistFromUrl(url: String) {
        if (url.isBlank()) return
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyPlaylistOutcome(playlistRepository.loadFromUrl(url.trim()))
        }
    }

    fun loadPlaylistFromFile(uri: Uri) {
        _playlistState.value = _playlistState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            applyPlaylistOutcome(playlistRepository.loadFromFile(uri))
        }
    }

    fun selectEpgSource(source: EpgSource) {
        preferences.epgSource = source
        _epgState.update { it.copy(selectedSource = source, isLoading = true) }
        viewModelScope.launch {
            applyEpgOutcome(epgRepository.loadFromSource(source))
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

    fun isFavorite(channel: M3uChannel): Boolean = favoritesRepository.isFavorite(channel)

    fun toggleFavorite(channel: M3uChannel) = favoritesRepository.toggleFavorite(channel)

    fun removeFavorite(key: String) = favoritesRepository.remove(key)

    fun setIconDisplayMode(mode: IconDisplayMode) {
        preferences.iconDisplayMode = mode
        _settingsState.update { it.copy(iconDisplayMode = mode) }
    }

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
        _settingsState.update {
            it.copy(
                deviceTier = effectiveTier,
                iconDisplayMode = resolvedIconDisplayMode(effectiveTier),
                listDensity = resolvedListDensity(effectiveTier),
            )
        }
    }

    fun setChannelLayout(layout: ChannelLayout) {
        preferences.channelLayout = layout
        _settingsState.update { it.copy(channelLayout = layout) }
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

    fun clearCache(kind: CacheKind) {
        val filesDir = getApplication<Application>().filesDir
        val file = when (kind) {
            CacheKind.PLAYLIST -> File(filesDir, CachePaths.PLAYLIST_SNAPSHOT)
            CacheKind.EPG -> File(filesDir, CachePaths.EPG_SNAPSHOT)
            CacheKind.ICONS -> File(filesDir, CachePaths.ICON_CACHE_DIR)
            CacheKind.COIL -> File(filesDir, CachePaths.COIL_CACHE_DIR)
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CacheSizeUtils.clear(file) }
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

    private fun triggerIconPrefetch(channels: List<M3uChannel>) {
        lastPrefetchChannels = channels
        unmeteredNetworkWatcher?.close()
        unmeteredNetworkWatcher = iconPrefetcher.awaitUnmeteredNetwork {
            viewModelScope.launch { runIconPrefetch(lastPrefetchChannels) }
        }
        viewModelScope.launch { runIconPrefetch(channels) }
    }

    private suspend fun runIconPrefetch(channels: List<M3uChannel>) {
        if (channels.isEmpty()) return
        if (settingsState.value.iconDisplayMode != IconDisplayMode.CACHE) return
        _iconPrefetchState.update { it.copy(isRunning = true, completed = 0, total = channels.size) }
        iconPrefetcher.prefetch(channels, preferences.iconWifiOnly) { progress ->
            _iconPrefetchState.update { it.copy(completed = progress.completed, total = progress.total) }
        }
        preferences.lastIconPrefetchAtMillis = System.currentTimeMillis()
        _iconPrefetchState.update { it.copy(isRunning = false, updateReminderDue = false) }
        refreshCacheSizes()
    }

    override fun onCleared() {
        unmeteredNetworkWatcher?.close()
    }

    private fun applyPlaylistOutcome(outcome: PlaylistOutcome) {
        _playlistState.value = when (outcome) {
            is PlaylistOutcome.Loaded -> {
                val channels = outcome.groups.flatMap { it.channels }
                playlistChannelCount = channels.size
                recomputeDeviceTierDefaults()
                triggerIconPrefetch(channels)
                PlaylistUiState(
                    groups = outcome.groups,
                    isLoading = false,
                    skippedLineCount = outcome.skippedLineCount,
                    error = null,
                )
            }
            PlaylistOutcome.SizeLimitExceeded -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.SizeLimitExceeded,
            )
            is PlaylistOutcome.HttpError -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.Http(outcome.code),
            )
            is PlaylistOutcome.ReadError -> _playlistState.value.copy(
                isLoading = false,
                error = PlaylistError.Network,
            )
        }
        refreshCacheSizes()
    }

    private fun applyEpgOutcome(outcome: EpgOutcome) {
        _epgState.update { current ->
            when (outcome) {
                is EpgOutcome.Loaded -> current.copy(data = outcome.data, isLoading = false)
                else -> current.copy(isLoading = false)
            }
        }
        if (outcome is EpgOutcome.Loaded) {
            epgProgrammeCount = outcome.data.programmesByChannelId.values.sumOf { it.size }
            recomputeDeviceTierDefaults()
        }
        refreshCacheSizes()
    }
}
