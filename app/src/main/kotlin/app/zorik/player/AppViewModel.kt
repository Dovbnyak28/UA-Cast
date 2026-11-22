package app.zorik.player

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zorik.player.app.BackupController
import app.zorik.player.app.EpgController
import app.zorik.player.app.GroupVisibilityController
import app.zorik.player.app.IconController
import app.zorik.player.app.ParentalControlController
import app.zorik.player.app.PlaylistController
import app.zorik.player.app.SettingsController
import app.zorik.player.app.GuidedTourController
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.data.premium.FakeBillingProvider
import app.zorik.player.data.premium.PremiumRepository
import app.zorik.player.premium.DeveloperMode
import app.zorik.player.premium.Entitlements
import app.zorik.player.data.premium.PlayBillingProvider
import app.zorik.player.premium.PremiumAvailability
import app.zorik.player.premium.Feature
import app.zorik.player.premium.FeatureManager
import app.zorik.player.premium.billing.BillingConnectionState
import app.zorik.player.premium.billing.BillingProduct
import app.zorik.player.premium.billing.PurchaseResult
import app.zorik.player.backup.BackupData
import app.zorik.player.backup.BackupExportResult
import app.zorik.player.backup.BackupImportSummary
import app.zorik.player.cast.CastPlaybackState
import app.zorik.player.cast.CastSessionRepository
import app.zorik.player.core.concurrent.AppDispatchers
import app.zorik.player.core.concurrent.LatestResultGuard
import app.zorik.player.ui.theme.appTheme
import app.zorik.player.data.cache.CacheSizeUtils
import app.zorik.player.data.epg.EpgRepository
import app.zorik.player.data.favorites.FavoritesRepository
import app.zorik.player.data.icons.IconPrefetcher
import app.zorik.player.data.icons.IconRepository
import app.zorik.player.data.parentalcontrol.ParentalControlStore
import app.zorik.player.data.playlist.GroupVisibilityStore
import app.zorik.player.data.playlist.PlaylistRepository
import app.zorik.player.data.prefs.AppPreferences
import app.zorik.player.data.prefs.DeviceSpecsProvider
import app.zorik.player.diagnostics.DiagnosticsReportBuilder
import app.zorik.player.diagnostics.RemuxEffectivenessStore
import app.zorik.player.epg.EpgWorkloadPolicy
import app.zorik.player.epg.EpgUiState
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.performance.DevicePerformanceClassifier
import app.zorik.player.performance.DeviceTier
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistSource
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.settings.CacheKind
import app.zorik.player.settings.SettingsUiState
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Root view model for app-wide, cross-screen state (language, playlist/channels, EPG, icons,
 * favorites, settings). Screen-scoped state such as the player stack lives in its own view model
 * instead.
 *
 * Playlist/EPG/icon/settings/backup concerns are delegated to dedicated controllers under
 * `app.zorik.player.app` (move-only split, see B1 in the consolidated fix plan) - this class holds
 * their instances, wires the cross-controller side effects between them (device-tier recompute,
 * icon prefetch, EPG auto-detect, cache-size refresh), and exposes the exact same public API it
 * always did so the UI layer needed no changes.
 */
class AppViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) : AndroidViewModel(application) {

    internal val preferences = AppPreferences(application)
    internal val remuxEffectivenessStore = RemuxEffectivenessStore.getInstance(application)
    private val playlistRepository = PlaylistRepository(application, ioDispatcher)
    private val epgRepository = EpgRepository(application, ioDispatcher)
    private val iconRepository = IconRepository(application, ioDispatcher)
    private val iconPrefetcher = IconPrefetcher(application, iconRepository)
    internal val favoritesRepository = FavoritesRepository(application, viewModelScope)
    private val groupVisibilityStore = GroupVisibilityStore(application, ioDispatcher)
    private val cacheSizeRefreshGeneration = LatestResultGuard()
    private var cacheSizeRefreshJob: Job? = null

    private val baseDeviceTier: DeviceTier = DeviceSpecsProvider.current(application).let { specs ->
        DevicePerformanceClassifier.classify(specs.totalRamBytes, specs.cpuCoreCount, specs.sdkInt)
    }

    val castState: StateFlow<CastPlaybackState> = CastSessionRepository.getInstance(application).state
    val favorites: StateFlow<List<FavoriteChannel>> = favoritesRepository.favorites

    internal val playlistController: PlaylistController = PlaylistController(
        preferences = preferences,
        playlistRepository = playlistRepository,
        scope = viewModelScope,
        onLoaded = { channels, groups, epgUrls, fromCache ->
            favoritesRepository.refreshMetadata(channels)
            // Do not download/parse an EPG on a fresh install that has no channels to match it to.
            // This is idempotent, so later playlist switches keep the already selected guide.
            epgController.loadInitial()
            recomputeDeviceTierDefaults(channels)
            iconController.triggerPrefetch(
                channels,
                settingsState.value.iconDisplayMode,
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

    internal val groupVisibilityController = GroupVisibilityController(groupVisibilityStore, viewModelScope)
    val pinnedGroupKeys: StateFlow<Set<String>> = groupVisibilityController.pinnedKeys
    val hiddenGroupKeys: StateFlow<Set<String>> = groupVisibilityController.hiddenKeys

    internal val guidedTourController = GuidedTourController(storage = preferences)
    val guidedTourState: StateFlow<GuidedTourState> = guidedTourController.state
    val hasSeenGuidedTour: StateFlow<Boolean> = guidedTourController.hasSeenTour

    /**
     * Premium access. There is no `PremiumController` beside the other controllers on purpose:
     * [PremiumRepository] already is one - it owns the state, the scope and the side effects - and a
     * class that only forwarded to it would be ceremony rather than structure.
     *
     * [PremiumAvailability.STORE_IS_LIVE] selects real Google Play checkout or the unavailable
     * provider. Lite/Premium feature access comes from the licence in both cases.
     *
     * It is a `const`, so R8 folds this branch: a release build with the store off does not carry
     * the billing client's code paths at all, and one with it on does not carry the fake.
     */
    private val premiumRepository = PremiumRepository(
        provider = if (PremiumAvailability.STORE_IS_LIVE) {
            PlayBillingProvider(application, viewModelScope)
        } else {
            FakeBillingProvider()
        },
        storage = preferences,
        scope = viewModelScope,
    )
    val entitlements: StateFlow<Entitlements> = premiumRepository.entitlements

    /** Whether a store can be reached, which is what lets the premium screen tell "not published
     * yet" apart from "this device has no Google Play" - see [app.zorik.player.premium.StoreAbsence]. */
    val premiumConnection: StateFlow<BillingConnectionState> = premiumRepository.connection

    /** Screens ask this manager about the current Lite/Premium entitlement. */
    val featureManager = FeatureManager(entitlements)

    private val _premiumProducts = MutableStateFlow<List<BillingProduct>>(emptyList())

    /** What the store offers. Empty until something asks - and empty forever while there is no
     * store, which the premium screen says out loud rather than showing an empty price list. */
    val premiumProducts: StateFlow<List<BillingProduct>> = _premiumProducts.asStateFlow()

    /** Re-check legacy expiry when returning from the background or device clock settings. */
    fun refreshPremiumAccess() = premiumRepository.refresh()

    internal val parentalControlController =
        ParentalControlController(
            ParentalControlStore(application, ioDispatcher),
            preferences,
            viewModelScope,
        )
    val lockedChannelKeys: StateFlow<Set<String>> = parentalControlController.lockedKeys
    val parentalControlPinSet: StateFlow<Boolean> = parentalControlController.isPinSet
    val parentalControlUnlocked: StateFlow<Boolean> = parentalControlController.unlockedThisSession
    val parentalControlReady: StateFlow<Boolean> = parentalControlController.isReady

    internal val epgController = EpgController(
        preferences = preferences,
        epgRepository = epgRepository,
        scope = viewModelScope,
        isUnmeteredNetwork = { AppNetworkInfo.isUnmetered(getApplication<Application>()) },
        onLoaded = {
            recomputeDeviceTierDefaults(loadedPlaylistChannels())
            refreshCacheSizes()
        },
    )
    val epgState: StateFlow<EpgUiState> = epgController.epgState

    internal val iconController = IconController(
        preferences = preferences,
        iconRepository = iconRepository,
        iconPrefetcher = iconPrefetcher,
        scope = viewModelScope,
        onPrefetchFinished = ::refreshCacheSizes,
    )
    val iconPrefetchState: StateFlow<IconPrefetchUiState> = iconController.iconPrefetchState

    internal val settingsController = SettingsController(preferences, iconController.sources, baseDeviceTier)
    val settingsState: StateFlow<SettingsUiState> = settingsController.settingsState

    internal val backupController = BackupController(
        application,
        favoritesRepository,
        viewModelScope,
        ioDispatcher,
        awaitSourcesLoaded = { playlistController.sourceInitialization.awaitLoaded() == null },
    )
    val backupImportSummary: StateFlow<BackupImportSummary?> = backupController.backupImportSummary
    val backupExportResult: StateFlow<BackupExportResult?> = backupController.backupExportResult
    internal val backupRestoreWorkflow = app.zorik.player.app.BackupRestoreWorkflow(
        scope = viewModelScope,
        read = { uri -> withContext(ioDispatcher) {
            app.zorik.player.data.backup.BackupDocumentReader.read(application, uri, currentCoroutineContext().job)
        } },
        prepare = { data ->
            if (playlistController.sourceInitialization.awaitLoaded() == null) {
                favoritesRepository.awaitLoaded()
                backupController.preparePortableData(data)
            } else null
        },
        current = ::buildBackupData,
        apply = { data -> if (Feature.BACKUP in entitlements.value.unlocked) backupController.importDecoded(
            data,
            app.zorik.player.app.BackupRestoreTarget(
                currentSources = { playlistSources.value },
                currentFavorites = { favorites.value },
                onSourcesMerged = { playlistController.applyImportedSources(it, activateIfNeeded = true).join() },
                onSettingsImported = ::applyImportedSettings,
                awaitSourcesPersisted = playlistController::awaitSourcesPersistence,
            ),
        ) else viewModelScope.launch {} },
    )

    // AppPreferences is a plain synchronous wrapper, not itself observable, and the player that
    // writes lastWatchedChannelKey is a separate ViewModel instance - so this needs an explicit
    // refresh (see refreshLastWatchedChannel()) rather than picking up the write automatically.
    internal val lastWatchedChannelKeyMutable = MutableStateFlow(preferences.lastWatchedChannelKey)
    val lastWatchedChannelKey: StateFlow<String?> = lastWatchedChannelKeyMutable.asStateFlow()

    /**
     * Reads the latest persisted player channel for restoration. The player is a separate,
     * Activity-scoped ViewModel and writes this preference on every channel switch, so the flow
     * above is intentionally refreshed only when the player closes. Process recreation must use
     * the synchronous value instead of that possibly stale in-memory mirror.
     */
    internal fun persistedLastWatchedChannelKey(): String? = preferences.lastWatchedChannelKey

    internal val uiStateMutable = MutableStateFlow(
        AppUiState(
            language = preferences.language,
            appTheme = preferences.appTheme,
            needsLanguagePicker = !preferences.hasChosenLanguage,
            needsTermsAcceptance = !preferences.hasAcceptedTerms,
        )
    )
    val uiState: StateFlow<AppUiState> = uiStateMutable.asStateFlow()

    internal val showBatteryOptimizationHintMutable = MutableStateFlow(false)
    val showBatteryOptimizationHint: StateFlow<Boolean> = showBatteryOptimizationHintMutable.asStateFlow()

    init {
        playlistController.loadInitialSource()
        epgController.startTicking()
        groupVisibilityController.loadInitial()
        parentalControlController.loadInitial()
        // Starts in Lite or restores the stored paid licence, then listens for ownership changes.
        premiumRepository.loadInitial()
        viewModelScope.launch(ioDispatcher) { AppCacheFiles.deleteRetiredUpdateDownloads(application.cacheDir) }
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

    /** Re-reads what the store offers. Called when a premium surface opens rather than at startup:
     * an app that never shows the premium screen should not talk to a store at all. */
    fun refreshPremiumProducts() {
        viewModelScope.launch { _premiumProducts.value = premiumRepository.products() }
    }

    private val storeAttempts = PremiumStoreAttempts(viewModelScope)

    /** How the last purchase or restore ended, until a screen has shown it - see
     * [PremiumStoreAttempts.lastPurchaseOutcome]. */
    val lastPurchaseOutcome: StateFlow<PurchaseResult?> = storeAttempts.lastPurchaseOutcome

    /** Whether an attempt is with the store right now - see [PremiumStoreAttempts.isPurchasing]. */
    val isPurchasing: StateFlow<Boolean> = storeAttempts.isPurchasing

    fun purchasePremium(product: BillingProduct, launchContext: Any?) = storeAttempts.startStoreAttempt {
        premiumRepository.purchase(product.id, launchContext)
    }

    fun restorePremiumPurchases() = storeAttempts.startStoreAttempt { premiumRepository.restore() }

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
        // an offline/expired legacy licence is not a current purchase - so the stored
        // license has to be re-read rather than waited for.
        premiumRepository.refresh()
    }

    /** Taken once, at construction, so the report can say how long the app had been running - which
     * is what tells a reader whether the log below could contain the moment being reported. */
    internal val processStartedAtMillis = android.os.SystemClock.elapsedRealtime()

    /** Builds the "Send diagnostics" report text (see HelpScreen) - see [diagnosticsSnapshot]. */
    fun buildDiagnosticsReport(): String = DiagnosticsReportBuilder.build(diagnosticsSnapshot())

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

    /**
     * @param playlistChannels the channels the tier should be judged against. Passed in by the
     *   playlist's own `onLoaded` because [playlistState] has not been updated with the new groups
     *   yet at that moment (the same ordering [prefetchContext] documents); the EPG's `onLoaded`
     *   fires later and reads them from the state, which by then is settled. Before a playlist
     *   exists this is empty, the guide counts as nothing, and no downgrade is applied - which is
     *   correct: an empty playlist is not a heavy one.
     */
    private fun recomputeDeviceTierDefaults(playlistChannels: List<M3uChannel>) {
        val effectiveTier = DevicePerformanceClassifier.adjustForContentSize(
            baseDeviceTier,
            playlistController.channelCount,
            // The guide for THIS playlist, not the feed's total - see EpgWorkloadPolicy for the
            // field report where the difference was 311 channels against 4052.
            EpgWorkloadPolicy.programmesFor(epgState.value.data, playlistChannels),
        )
        settingsController.recomputeDeviceTierDefaults(effectiveTier)
    }

    private fun loadedPlaylistChannels(): List<M3uChannel> =
        playlistState.value.channels

    fun exportBackupTo(uri: Uri, password: CharArray? = null) =
        backupController.exportCurrentTo(uri, password, ::buildBackupData)

    fun importBackupFrom(uri: Uri) {
        backupController.importFrom(
            uri = uri,
            currentSources = { playlistSources.value },
            currentFavorites = { favorites.value },
            onSourcesMerged = { playlistController.applyImportedSources(it, activateIfNeeded = true).join() },
            onSettingsImported = ::applyImportedSettings,
            awaitSourcesPersisted = playlistController::awaitSourcesPersistence,
        )
    }

    private fun buildBackupData(): BackupData =
        AppBackupMapping.backupData(playlistSources.value, favorites.value, settingsState.value, preferences)

    fun clearCache(kind: CacheKind) {
        val filesDir = getApplication<Application>().filesDir
        val files = AppCacheFiles.filesFor(kind, filesDir)
        // A size walk started before this delete describes a filesystem state that is no longer
        // relevant. Cancel it for efficiency and invalidate it independently because File.walk is
        // not cooperatively cancellable while it is traversing a large directory.
        cacheSizeRefreshGeneration.invalidate()
        cacheSizeRefreshJob?.cancel()
        // Installed synchronously before launching so a network/playback callback cannot slip a
        // new writer between cancellation and the delete below. The barrier also remembers every
        // older cancelled generation which may still be unwinding, not just the latest Job.
        val iconCacheClearBarrier = if (kind == CacheKind.ICONS) {
            iconController.beginIconCacheClear()
        } else {
            null
        }
        val cacheClearJob = viewModelScope.launch {
            // Cancellation is cooperative, not instant - wait until every captured writer has
            // actually unwound before deleting its directory.
            iconCacheClearBarrier?.awaitStopped()
            when (kind) {
                CacheKind.PLAYLIST -> playlistRepository.clearSnapshots()
                CacheKind.EPG -> epgRepository.clearSnapshots()
                else -> withContext(ioDispatcher) { CacheSizeUtils.clear(files) }
            }
            // The deleted files are exactly what resolveChannelIcon's in-memory cache may still
            // hold (positive results pointing at gone files, or negative results which should
            // be retried), so the next resolution must consult disk again.
            if (kind == CacheKind.ICONS) iconController.invalidateMemoryCache()
            refreshCacheSizes()
        }
        // A completion handler also runs when launch is born cancelled and never enters its body;
        // that closes the tiny onCleared-vs-clearCache window without requiring a suspend finally.
        iconCacheClearBarrier?.let { barrier ->
            cacheClearJob.invokeOnCompletion { iconController.finishIconCacheClear(barrier) }
        }
    }

    private fun refreshCacheSizes() {
        val generation = cacheSizeRefreshGeneration.next()
        cacheSizeRefreshJob?.cancel()
        cacheSizeRefreshJob = viewModelScope.launch {
            val filesDir = getApplication<Application>().filesDir
            val sizes = withContext(ioDispatcher) { AppCacheFiles.measure(filesDir) }
            // Cache writes/clears can request another measurement while this non-cancellable file
            // walk is still unwinding. Never let that older snapshot overwrite the newer result.
            if (cacheSizeRefreshGeneration.isCurrent(generation)) {
                settingsController.updateCacheSizes(sizes)
            }
        }
    }

    override fun onCleared() {
        premiumRepository.close()
        iconController.dispose()
        super.onCleared()
    }
}
