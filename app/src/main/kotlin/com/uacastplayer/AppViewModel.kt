package com.uacastplayer

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uacastplayer.app.BackupController
import com.uacastplayer.app.EpgController
import com.uacastplayer.app.GroupVisibilityController
import com.uacastplayer.app.IconController
import com.uacastplayer.app.ParentalControlController
import com.uacastplayer.app.PlaylistController
import com.uacastplayer.app.SettingsController
import com.uacastplayer.app.UpdateController
import com.uacastplayer.data.premium.FakeBillingProvider
import com.uacastplayer.data.premium.PremiumRepository
import com.uacastplayer.data.update.UpdateRepository
import com.uacastplayer.premium.DeveloperMode
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.FeatureManager
import com.uacastplayer.premium.billing.BillingConnectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.update.UpdateUiState
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupFavorite
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.cast.CastSessionRepository
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.data.cache.CachePaths
import com.uacastplayer.data.cache.CacheSizeUtils
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.parentalcontrol.ParentalControlStore
import com.uacastplayer.data.playlist.GroupVisibilityStore
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.DeviceSpecsProvider
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.diagnostics.DiagnosticsReportBuilder
import com.uacastplayer.diagnostics.DiagnosticsSnapshot
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.diagnostics.RemuxEffectivenessStore
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.log.LogBuffer
import com.uacastplayer.performance.DevicePerformanceClassifier
import com.uacastplayer.performance.DeviceTier
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.CacheSizes
import com.uacastplayer.settings.SettingsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val needsLanguagePicker: Boolean = true,
    val needsTermsAcceptance: Boolean = true,
    val needsOnboarding: Boolean = true,
)

/**
 * Root view model for app-wide, cross-screen state (language, playlist/channels, EPG, icons,
 * favorites, settings). Screen-scoped state such as the player stack lives in its own view model
 * instead.
 *
 * Playlist/EPG/icon/settings/backup concerns are delegated to dedicated controllers under
 * `com.uacastplayer.app` (move-only split, see B1 in the consolidated fix plan) - this class holds
 * their instances, wires the cross-controller side effects between them (device-tier recompute,
 * icon prefetch, EPG auto-detect, cache-size refresh), and exposes the exact same public API it
 * always did so the UI layer needed no changes.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val remuxEffectivenessStore = RemuxEffectivenessStore.getInstance(application)
    private val playlistRepository = PlaylistRepository(application)
    private val epgRepository = EpgRepository(application)
    private val iconRepository = IconRepository(application)
    private val iconPrefetcher = IconPrefetcher(application, iconRepository)
    private val favoritesRepository = FavoritesRepository(application)
    private val groupVisibilityStore = GroupVisibilityStore(application)

    private val baseDeviceTier: DeviceTier = DeviceSpecsProvider.current(application).let { specs ->
        DevicePerformanceClassifier.classify(specs.totalRamBytes, specs.cpuCoreCount, specs.sdkInt)
    }

    val castState: StateFlow<CastPlaybackState> = CastSessionRepository.getInstance(application).state
    val favorites: StateFlow<List<FavoriteChannel>> = favoritesRepository.favorites

    private val playlistController: PlaylistController = PlaylistController(
        preferences = preferences,
        playlistRepository = playlistRepository,
        scope = viewModelScope,
        onLoaded = { channels, groups, epgUrls, fromCache ->
            recomputeDeviceTierDefaults()
            iconController.triggerPrefetch(
                channels,
                settingsState.value.iconDisplayMode,
                ::epgIconUrlFor,
                prefetchContext(groups.firstOrNull()?.channels.orEmpty()),
            )
            // Only on an actual load, never a startup cache restore - see
            // EpgController.handleEpgAutoDetect's doc.
            if (!fromCache) epgController.handleEpgAutoDetect(epgUrls)
        },
        onStateChanged = ::refreshCacheSizes,
    )
    val playlistState: StateFlow<PlaylistUiState> = playlistController.playlistState
    val playlistSources: StateFlow<List<PlaylistSource>> = playlistController.playlistSources
    val activePlaylistSourceId: StateFlow<String?> = playlistController.activePlaylistSourceId

    private val groupVisibilityController = GroupVisibilityController(groupVisibilityStore, viewModelScope)
    val pinnedGroupKeys: StateFlow<Set<String>> = groupVisibilityController.pinnedKeys
    val hiddenGroupKeys: StateFlow<Set<String>> = groupVisibilityController.hiddenKeys

    private val updateController = UpdateController(
        releaseSource = UpdateRepository(),
        storage = preferences,
        scope = viewModelScope,
        installedVersionName = BuildConfig.VERSION_NAME,
    )
    val updateState: StateFlow<UpdateUiState> = updateController.state

    /**
     * Premium access. There is no `PremiumController` beside the other controllers on purpose:
     * [PremiumRepository] already is one - it owns the state, the scope and the side effects - and a
     * class that only forwarded to it would be ceremony rather than structure.
     *
     * The provider is the one line that changes on the day Google Play Billing is turned on. Until
     * then [FakeBillingProvider] reports, truthfully, that there is no store: nothing is for sale
     * yet.
     */
    private val premiumRepository = PremiumRepository(
        provider = FakeBillingProvider(),
        storage = preferences,
        scope = viewModelScope,
    )
    val entitlements: StateFlow<Entitlements> = premiumRepository.entitlements

    /** The only way anything in this app asks whether a feature is available. */
    val featureManager = FeatureManager(entitlements)

    val premiumConnection: StateFlow<BillingConnectionState> = premiumRepository.connection

    private val _premiumProducts = MutableStateFlow<List<BillingProduct>>(emptyList())

    /** What the store offers. Empty until something asks - and empty forever while there is no
     * store, which the premium screen says out loud rather than showing an empty price list. */
    val premiumProducts: StateFlow<List<BillingProduct>> = _premiumProducts.asStateFlow()

    private val parentalControlController =
        ParentalControlController(ParentalControlStore(application), preferences, viewModelScope)
    val lockedChannelKeys: StateFlow<Set<String>> = parentalControlController.lockedKeys
    val parentalControlPinSet: StateFlow<Boolean> = parentalControlController.isPinSet
    val parentalControlUnlocked: StateFlow<Boolean> = parentalControlController.unlockedThisSession

    private val epgController = EpgController(
        preferences = preferences,
        epgRepository = epgRepository,
        scope = viewModelScope,
        onLoaded = {
            recomputeDeviceTierDefaults()
            refreshCacheSizes()
            // The initial prefetch (triggered right after the playlist loads - see
            // PlaylistController's onLoaded above) fires before EPG data exists for a fresh load,
            // so channels whose only icon comes from the EPG match (the "built-in" cdn.epg.one
            // source, resolved via epgIconUrlFor - see IconResolver) never got a chance at a bulk
            // background fetch: they only picked up their icon later, one row at a time, whenever
            // the user happened to scroll past that channel. Re-running prefetch here - now that
            // an epgIconUrlFor lookup actually resolves to something - gives those icons the same
            // bulk background download (with the same visible banner) instead.
            val groups = playlistController.playlistState.value.groups
            val channels = groups.flatMap { it.channels }
            if (channels.isNotEmpty()) {
                iconController.triggerPrefetch(
                    channels,
                    settingsState.value.iconDisplayMode,
                    ::epgIconUrlFor,
                    prefetchContext(groups.firstOrNull()?.channels.orEmpty()),
                )
            }
        },
    )
    val epgState: StateFlow<EpgUiState> = epgController.epgState

    private val iconController = IconController(
        preferences = preferences,
        iconRepository = iconRepository,
        iconPrefetcher = iconPrefetcher,
        scope = viewModelScope,
        onPrefetchFinished = ::refreshCacheSizes,
    )
    val iconPrefetchState: StateFlow<IconPrefetchUiState> = iconController.iconPrefetchState

    private val settingsController = SettingsController(preferences, iconController, baseDeviceTier)
    val settingsState: StateFlow<SettingsUiState> = settingsController.settingsState

    private val backupController = BackupController(application, favoritesRepository, viewModelScope)
    val backupImportSummary: StateFlow<BackupImportSummary?> = backupController.backupImportSummary

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
            needsOnboarding = !preferences.hasSeenOnboarding,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _showBatteryOptimizationHint = MutableStateFlow(false)
    val showBatteryOptimizationHint: StateFlow<Boolean> = _showBatteryOptimizationHint.asStateFlow()

    init {
        playlistController.loadInitialSource()
        epgController.loadInitial()
        epgController.startTicking()
        groupVisibilityController.loadInitial()
        parentalControlController.loadInitial()
        // At most one request a week, and only ever adds a banner - see UpdateController.
        updateController.checkOnLaunch()
        // Grants the first-launch trial if this device has never held a license, then starts
        // listening to whatever store there is.
        premiumRepository.loadInitial()
        viewModelScope.launch {
            activePlaylistSourceId.collect { groupVisibilityController.setActiveSource(it) }
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

    /** The Settings button. Unlike the weekly check this ignores the throttle and reports failure -
     * see [UpdateController]. */
    fun checkForUpdatesNow() = updateController.checkNow()

    /** Closes the update banner for that release only. */
    fun dismissUpdateBanner() = updateController.dismissAvailableUpdate()

    /** Clears the one-shot result line under the Settings button once it has been read. */
    fun clearUpdateCheckOutcome() = updateController.clearLastOutcome()

    /** Re-reads what the store offers. Called when a premium surface opens rather than at startup:
     * an app that never shows the premium screen should not talk to a store at all. */
    fun refreshPremiumProducts() {
        viewModelScope.launch { _premiumProducts.value = premiumRepository.products() }
    }

    fun purchasePremium(product: BillingProduct, launchContext: Any?) {
        viewModelScope.launch { premiumRepository.purchase(product.id, launchContext) }
    }

    fun restorePremiumPurchases() {
        viewModelScope.launch { premiumRepository.restore() }
    }

    /** License states the developer menu can force this build into - empty in a release build, where
     * the code that fills [DeveloperMode] is not compiled at all. */
    val developerLicenseStates: List<String> get() = DeveloperMode.states

    /**
     * Puts the app into one of [developerLicenseStates]. A no-op when there is no developer menu,
     * which is the only state a release build can be in.
     */
    fun applyDeveloperLicenseState(state: String) {
        val provider = DeveloperMode.apply?.invoke(state, preferences) ?: return
        premiumRepository.useProvider(provider)
        // The state may have been written straight to storage rather than reported as a purchase -
        // a trial and an expired subscription are not things a store can express - so the stored
        // license has to be re-read rather than waited for.
        premiumRepository.refresh()
    }

    /** Builds the "Send diagnostics" report text (see HelpScreen) from whatever is already known
     * synchronously - current settings, device tier, and [LogBuffer]'s recent entries - without
     * touching anything that isn't already read elsewhere in this ViewModel. */
    fun buildDiagnosticsReport(): String {
        val runtime = Runtime.getRuntime()
        return DiagnosticsReportBuilder.build(
            DiagnosticsSnapshot(
                appVersionName = BuildConfig.VERSION_NAME,
                deviceModel = Build.MODEL,
                androidApiLevel = Build.VERSION.SDK_INT,
                deviceTier = settingsState.value.deviceTier,
                bufferSize = settingsState.value.bufferSize,
                iconDisplayMode = settingsState.value.iconDisplayMode,
                appTheme = uiState.value.appTheme,
                usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory(),
                totalMemoryBytes = runtime.totalMemory(),
                maxMemoryBytes = runtime.maxMemory(),
                logEntries = LogBuffer.snapshot(),
                remuxEffectiveness = remuxEffectivenessStore.snapshot(),
            ),
        )
    }

    /** Read-only routing stats for Settings -> Diagnostics - see [RemuxEffectivenessStore]. */
    fun remuxEffectivenessSnapshot(): RemuxEffectivenessCounts = remuxEffectivenessStore.snapshot()

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

    /** Skipping and completing both end onboarding the same way - see [OnboardingScreen]. */
    fun completeOnboarding() {
        preferences.hasSeenOnboarding = true
        _uiState.value = _uiState.value.copy(needsOnboarding = false)
    }

    fun setPlaylistDisplayName(name: String) = playlistController.setPlaylistDisplayName(name)

    fun loadPlaylistFromUrl(url: String) = playlistController.loadPlaylistFromUrl(url)

    fun loadXtreamPlaylist(server: String, username: String, password: String) =
        playlistController.loadXtreamPlaylist(server, username, password)

    fun loadPlaylistFromFile(uri: Uri) = playlistController.loadPlaylistFromFile(uri)

    fun refreshPlaylist() = playlistController.refreshPlaylist()

    fun switchPlaylistSource(source: PlaylistSource) = playlistController.switchPlaylistSource(source)

    fun removePlaylistSource(id: String) = playlistController.removePlaylistSource(id)

    fun pinGroup(groupKey: String) = groupVisibilityController.pinGroup(groupKey)

    fun hideGroup(groupKey: String) = groupVisibilityController.hideGroup(groupKey)

    /** Clears any pin/hide override for [groupKey] in the active source, returning it to the
     * default order/visibility - used both to unpin and to restore a hidden group. */
    fun clearGroupOverride(groupKey: String) = groupVisibilityController.clearOverride(groupKey)

    fun isChannelLocked(channel: M3uChannel): Boolean = parentalControlController.isLocked(FavoriteKey.of(channel))

    fun lockChannel(channel: M3uChannel) = parentalControlController.lockChannel(FavoriteKey.of(channel))

    /** Caller must already hold [parentalControlUnlocked] (see [verifyParentalControlPin]) - this
     * function doesn't re-check it, so a PIN entered once this session covers every subsequent
     * unlock without prompting again. */
    fun unlockChannelPermanently(channel: M3uChannel) =
        parentalControlController.unlockChannelPermanently(FavoriteKey.of(channel))

    /** True on a correct PIN, which also flips [parentalControlUnlocked] for the rest of this
     * process's lifetime - see [ParentalControlController]'s doc for why nothing else resets it. */
    suspend fun verifyParentalControlPin(pin: String): Boolean = parentalControlController.verifyPin(pin)

    /** Sets the PIN for the first time, or replaces an existing one - callers must gate a
     * *replacement* behind [parentalControlUnlocked] themselves (Settings' "change PIN" flow does).
     * False if [pin] isn't 4 digits. */
    suspend fun setParentalControlPin(pin: String): Boolean = parentalControlController.setPin(pin)

    /** The "forgot PIN" escape hatch - clears the PIN and every locked channel, no PIN required.
     * Settings' own confirmation dialog is the only guard before this is called. */
    fun resetParentalControl() = parentalControlController.resetParentalControl()

    fun selectEpgSource(source: EpgSource) = epgController.selectEpgSource(source)

    fun useSuggestedEpgUrl() = epgController.useSuggestedEpgUrl()

    fun setIconWifiOnly(enabled: Boolean) = iconController.setIconWifiOnly(enabled)

    /** The channel's icon URL from the matching EPG entry, if any - the "built-in" cdn.epg.one
     * source is only ever reachable this way (see [IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL]'s
     * own candidate being cache-only), so this is also passed to icon prefetch - see
     * [epgController]'s onLoaded above - not just to the interactive per-row resolve below. */
    private fun epgIconUrlFor(channel: M3uChannel): String? = epgState.value.data?.index?.match(channel)?.iconUrl

    /** Builds the "what's worth prefetching" context (see [IconController.PrefetchContext]) from
     * whatever's already known synchronously - [firstGroupChannels] is passed in rather than read
     * from [playlistState] here because the playlist-just-loaded call site fires before that state
     * flow itself has been updated with the newly loaded groups (see PlaylistController.applyPlaylistOutcome). */
    private fun prefetchContext(firstGroupChannels: List<M3uChannel>): IconController.PrefetchContext =
        IconController.PrefetchContext(
            favoriteKeys = favorites.value.map { it.key }.toSet(),
            lastWatchedKey = preferences.lastWatchedChannelKey,
            firstGroupChannels = firstGroupChannels,
            deviceTier = settingsState.value.deviceTier,
        )

    suspend fun resolveChannelIcon(channel: M3uChannel): File? {
        return iconController.resolveChannelIcon(channel, settingsState.value.iconDisplayMode, epgIconUrlFor(channel))
    }

    /**
     * The artwork URL for a channel being cast - handed to [PlayerViewModel] as a function rather
     * than a value on purpose. EPG data usually arrives *after* playback has already started, so a
     * URL resolved once at start time would be permanently missing the [epgIconUrlFor] half of the
     * chain for exactly the channels that need it. Called per channel switch, it sees whatever the
     * EPG knows by then.
     */
    fun castArtworkUrlFor(channel: M3uChannel): String? =
        iconController.castArtworkUrl(channel, epgIconUrlFor(channel))

    fun isFavorite(channel: M3uChannel): Boolean = favoritesRepository.isFavorite(channel)

    fun toggleFavorite(channel: M3uChannel) = favoritesRepository.toggleFavorite(channel)

    fun removeFavorite(key: String) = favoritesRepository.remove(key)

    fun reorderFavorites(newOrder: List<FavoriteChannel>) = favoritesRepository.reorder(newOrder)

    fun setIconDisplayMode(mode: IconDisplayMode) = settingsController.setIconDisplayMode(mode)

    fun dismissIconTierBanner() = settingsController.dismissIconTierBanner()

    fun setListDensity(density: ListDensity) = settingsController.setListDensity(density)

    private fun recomputeDeviceTierDefaults() {
        val effectiveTier = DevicePerformanceClassifier.adjustForContentSize(
            baseDeviceTier,
            playlistController.channelCount,
            epgController.programmeCount,
        )
        settingsController.recomputeDeviceTierDefaults(effectiveTier)
    }

    fun setChannelLayout(layout: ChannelLayout) = settingsController.setChannelLayout(layout)

    fun setBufferSize(size: BufferSize) = settingsController.setBufferSize(size)

    fun setFavoritesSortOrder(order: FavoritesSortOrder) = settingsController.setFavoritesSortOrder(order)

    fun addCustomIconSource(rawUrl: String) = settingsController.addCustomIconSource(rawUrl)

    fun removeCustomIconSource(url: String) = settingsController.removeCustomIconSource(url)

    fun dismissIconSourceError() = settingsController.dismissIconSourceError()

    fun setWrapAroundEnabled(enabled: Boolean) = settingsController.setWrapAroundEnabled(enabled)

    fun setAutoSkipDeadEnabled(enabled: Boolean) = settingsController.setAutoSkipDeadEnabled(enabled)

    fun exportBackupTo(uri: Uri) = backupController.exportTo(uri, buildBackupData())

    fun importBackupFrom(uri: Uri) {
        backupController.importFrom(
            uri = uri,
            currentSources = playlistSources.value,
            currentFavorites = favorites.value,
            onSourcesMerged = playlistController::applyImportedSources,
            onSettingsImported = ::applyImportedSettings,
        )
    }

    fun dismissBackupImportSummary() = backupController.dismissImportSummary()

    private fun buildBackupData(): BackupData {
        val sources = playlistSources.value.map {
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
            settings.epgCustomUrl != null -> epgController.applyCustomEpgUrl(settings.epgCustomUrl, markChosen = true)
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
        if (kind == CacheKind.ICONS) iconController.cancelPrefetch()
        viewModelScope.launch {
            // Cancellation is cooperative, not instant - this waits for the prefetch coroutine to
            // actually unwind so it can't still be mid-write into the directory this deletes.
            if (kind == CacheKind.ICONS) iconController.awaitPrefetchStopped()
            withContext(Dispatchers.IO) { CacheSizeUtils.clear(file) }
            // The deleted files are exactly what resolveChannelIcon's in-memory cache may still be
            // holding onto (positive results pointing at now-gone files, or negative results that
            // should be retried once the icon cache is empty) - drop it so the next resolve
            // actually looks at disk again instead of trusting stale in-memory entries.
            if (kind == CacheKind.ICONS) iconController.invalidateMemoryCache()
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
            settingsController.updateCacheSizes(sizes)
        }
    }

    override fun onCleared() {
        iconController.dispose()
        super.onCleared()
    }
}
