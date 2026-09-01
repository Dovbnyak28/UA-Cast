package com.uacastplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.ui.legal.HelpScreen
import com.uacastplayer.ui.legal.PrivacyPolicyScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.permissions.NotificationPermissionGate
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.update.UpdateSectionState

/** The main tab scaffold (Home/Channels/Favorites/Settings) plus the Help/Terms/AddPlaylist
 * full-screen overlays that temporarily replace it - collects every [RootScaffold] dependency
 * itself so a change to any of them (icon prefetch progress, cast state, EPG ticks, ...) only
 * recomposes this zone, not [PlayerZone] or [BatteryHintZone]. */
@Composable
@Suppress("LongParameterList") // glue for RootScaffold's own (out-of-scope-to-restructure) signature
internal fun ScaffoldZone(
    viewModel: AppViewModel,
    playlistState: PlaylistUiState,
    currentLanguage: AppLanguage,
    currentAppTheme: AppTheme,
    onExitApp: () -> Unit,
    showHelp: Boolean,
    showTerms: Boolean,
    showPrivacyPolicy: Boolean,
    showAddPlaylist: Boolean,
    focusChannelsToken: Int,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    onOpenAddPlaylist: () -> Unit,
    onCloseHelp: () -> Unit,
    onCloseTerms: () -> Unit,
    onClosePrivacyPolicy: () -> Unit,
    onCloseAddPlaylist: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onPlaylistLoaded: () -> Unit,
    pickPlaylistFile: () -> Unit,
    exportBackupFile: () -> Unit,
    importBackupFile: () -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
    premiumSection: PremiumSectionState,
    guidedTourDestination: BottomDestination?,
) {
    val playlistSources by viewModel.playlistSources.collectAsStateWithLifecycle()
    val activePlaylistSourceId by viewModel.activePlaylistSourceId.collectAsStateWithLifecycle()
    val pinnedGroupKeys by viewModel.pinnedGroupKeys.collectAsStateWithLifecycle()
    val hiddenGroupKeys by viewModel.hiddenGroupKeys.collectAsStateWithLifecycle()
    val lockedChannelKeys by viewModel.lockedChannelKeys.collectAsStateWithLifecycle()
    val parentalControlPinSet by viewModel.parentalControlPinSet.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
    val castState by viewModel.castState.collectAsStateWithLifecycle()
    // Asks for POST_NOTIFICATIONS the moment a cast starts, so CastProxyService's foreground
    // notification is actually visible on API 33+ - see NotificationPermissionGate.
    NotificationPermissionGate(castConnected = castState.isSessionConnected)
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val lastWatchedChannelKey by viewModel.lastWatchedChannelKey.collectAsStateWithLifecycle()
    val backupImportSummary by viewModel.backupImportSummary.collectAsStateWithLifecycle()
    val backupExportResult by viewModel.backupExportResult.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateInstallState by viewModel.updateInstallState.collectAsStateWithLifecycle()
    // Same reason as premiumSection - this reaches SettingsScreen, and a fresh instance on
    // every pass would stop it skipping.
    val hasSeenGuidedTour by viewModel.hasSeenGuidedTour.collectAsStateWithLifecycle()
    val guidedTourSection = remember(hasSeenGuidedTour) {
        GuidedTourSectionState(hasSeenTour = hasSeenGuidedTour, onStartTour = viewModel::startGuidedTour)
    }

    // LocalUriHandler rather than a raw ACTION_VIEW intent: it needs no queries entry in the
    // manifest, and it is the one place a device with no browser at all - a TV box, say - would
    // otherwise throw on what is meant to be an optional convenience.
    val uriHandler = LocalUriHandler.current
    // remembered for the same reason as premiumSection above: rebuilt on every pass it would be a
    // new instance each time, and this one reaches the top bar, so it would also re-invalidate the
    // update banner twice a minute for nothing.
    val context = LocalContext.current
    val updateSection = remember(updateState, updateInstallState, uriHandler, context) {
        UpdateSectionState(
            state = updateState,
            installState = updateInstallState,
            onCheckNow = viewModel::checkForUpdatesNow,
            onOpenRelease = { url ->
                try {
                    uriHandler.openUri(url)
                } catch (e: IllegalArgumentException) {
                    AppLog.w("MainActivity") { "no app can open the release page: ${e.javaClass.simpleName}" }
                }
            },
            onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
            onGrantInstallPermission = { openInstallPermissionSettings(context) },
            onDismissBanner = viewModel::dismissUpdateBanner,
            onOutcomeShown = {
                viewModel.clearUpdateCheckOutcome()
                viewModel.clearUpdateInstallOutcome()
            },
        )
    }

    // Derived from the flows collected right above rather than delegated to viewModel::isFavorite /
    // viewModel::isChannelLocked, which read StateFlow.value directly - a plain function call is not
    // a Compose state read, so a row's star/lock icon had no subscription to what it renders and
    // only ever refreshed because some ancestor happened to recompose the whole subtree anyway. Any
    // future skippability win in RootScaffold/ChannelsScreen would have silently frozen both badges
    // with no compile error to catch it. Keying the lambdas on the sets also gives Compose a real
    // signal that these inputs changed. HashSet, not the List: isFavorite runs once per visible row
    // per recomposition, and the favorites list is user-grown and unbounded.
    val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet(favorites.size)) { it.key } }
    val isFavorite = remember(favoriteKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in favoriteKeys }
    }
    val isChannelLocked = remember(lockedChannelKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in lockedChannelKeys }
    }
    // Cast routing counters only ever move during a cast session, so castState is the narrowest
    // signal that can mean "these may have changed". Called unkeyed, this ran on every recomposition
    // of this zone - i.e. on every EPG minute tick and every icon-prefetch progress update - while
    // still never actually refreshing reactively.
    val remuxEffectiveness = remember(castState) { viewModel.remuxEffectivenessSnapshot() }

    // Declared above the branch below, so that showing Help, Terms or the add-playlist screen does
    // not take the whole tab scaffold's state with it - see the doc on the `else` branch.
    val overlayStateHolder = rememberSaveableStateHolder()

    when {
        showHelp -> {
            BackHandler { onCloseHelp() }
            HelpScreen(onBackClick = onCloseHelp, onBuildDiagnosticsReport = viewModel::buildDiagnosticsReport)
        }

        showTerms -> {
            BackHandler { onCloseTerms() }
            TermsScreen(onBackClick = onCloseTerms)
        }

        showPrivacyPolicy -> {
            PrivacyPolicyScreen(onBackClick = onClosePrivacyPolicy)
        }

        showAddPlaylist -> {
            val cancelAndClose = {
                viewModel.cancelPendingPlaylistAdd()
                onCloseAddPlaylist()
            }
            BackHandler { cancelAndClose() }
            AddPlaylistScreen(
                playlistState = playlistState,
                onSetDisplayName = viewModel::setPlaylistDisplayName,
                onLoadUrl = viewModel::loadPlaylistFromUrl,
                onPickFile = pickPlaylistFile,
                onLoadXtream = viewModel::loadXtreamPlaylist,
                onBackClick = cancelAndClose,
                onPlaylistLoaded = onPlaylistLoaded,
            )
        }

        /**
         * Wrapped in a [SaveableStateProvider] because the three branches above genuinely do
         * replace it, and everything the user had set up inside went with it: the selected tab
         * (back to Home), the opened group, the group grid's scroll position, both search boxes.
         * Opening Help from Settings and pressing back landed on Home; adding a playlist from the
         * Channels tab and pressing back did the same, from a list the user had scrolled.
         *
         * `rememberSaveable` state only outlives a composable that leaves composition if something
         * holds it, and until now nothing did. The holder keeps this whole subtree's saved state -
         * including [RootScaffold]'s own per-tab holder, which is itself a `rememberSaveable` - so
         * one key restores the tab and everything that tab had on screen.
         *
         * The player is a different case and stays as it was: it never unmounts this at all (see
         * PlayerZone, drawn as a later sibling), so there is nothing to restore.
         */
        else -> overlayStateHolder.SaveableStateProvider(ROOT_SCAFFOLD_STATE_KEY) {
            RootScaffold(
                modifier = Modifier.fillMaxSize(),
                currentLanguage = currentLanguage,
                onLanguageSelected = viewModel::selectLanguage,
                currentAppTheme = currentAppTheme,
                onAppThemeSelected = viewModel::selectAppTheme,
                onExitApp = onExitApp,
                playlistState = playlistState,
                onOpenAddPlaylist = onOpenAddPlaylist,
                onRefreshPlaylist = viewModel::refreshPlaylist,
                playlistSources = playlistSources,
                activePlaylistSourceId = activePlaylistSourceId,
                onSwitchPlaylistSource = viewModel::switchPlaylistSource,
                onRemovePlaylistSource = { viewModel.removePlaylistSource(it.id) },
                pinnedGroupKeys = pinnedGroupKeys,
                hiddenGroupKeys = hiddenGroupKeys,
                onPinGroup = viewModel::pinGroup,
                onHideGroup = viewModel::hideGroup,
                onRestoreGroup = viewModel::clearGroupOverride,
                isChannelLocked = isChannelLocked,
                onLockChannel = viewModel::lockChannel,
                onUnlockChannel = { channel ->
                    requireParentalControlUnlock { viewModel.unlockChannelPermanently(channel) }
                },
                lockedChannelKeys = lockedChannelKeys,
                parentalControlPinSet = parentalControlPinSet,
                // A lambda, not a method reference: Kotlin will not adapt `::suspendFun` to a
                // `suspend (String) -> Boolean` parameter type.
                onSetParentalControlPin = { pin -> viewModel.setParentalControlPin(pin) },
                onResetParentalControl = viewModel::resetParentalControl,
                requireParentalControlUnlock = requireParentalControlUnlock,
                focusChannelsToken = focusChannelsToken,
                onChannelSelected = onChannelSelected,
                epgState = epgState,
                onEpgSourceSelected = viewModel::selectEpgSource,
                onUseSuggestedEpgUrl = viewModel::useSuggestedEpgUrl,
                iconPrefetchState = iconPrefetchState,
                onIconWifiOnlyChanged = viewModel::setIconWifiOnly,
                resolveIcon = viewModel::resolveChannelIcon,
                settingsState = settingsState,
                onIconDisplayModeSelected = viewModel::setIconDisplayMode,
                onDismissIconTierBanner = viewModel::dismissIconTierBanner,
                onListDensitySelected = viewModel::setListDensity,
                onChannelLayoutSelected = viewModel::setChannelLayout,
                onBufferSizeSelected = viewModel::setBufferSize,
                onFavoritesSortOrderSelected = viewModel::setFavoritesSortOrder,
                onWrapAroundChanged = viewModel::setWrapAroundEnabled,
                onAutoSkipChanged = viewModel::setAutoSkipDeadEnabled,
                onClearCache = viewModel::clearCache,
                onExportBackup = exportBackupFile,
                onImportBackup = importBackupFile,
                backupExportResult = backupExportResult,
                onDismissBackupExportResult = viewModel::dismissBackupExportResult,
                backupImportSummary = backupImportSummary,
                onDismissBackupImportSummary = viewModel::dismissBackupImportSummary,
                favorites = favorites,
                lastWatchedChannelKey = lastWatchedChannelKey,
                isFavorite = isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
                onRemoveFavorite = viewModel::removeFavorite,
                onReorderFavorites = viewModel::reorderFavorites,
                onOpenBatteryOptimizationHint = viewModel::reopenBatteryOptimizationHint,
                onAddIconSource = viewModel::addCustomIconSource,
                onRemoveIconSource = viewModel::removeCustomIconSource,
                onDismissIconSourceError = viewModel::dismissIconSourceError,
                onOpenHelp = onOpenHelp,
                onOpenTerms = onOpenTerms,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onBuildDiagnosticsReport = onBuildDiagnosticsReport,
                remuxEffectiveness = remuxEffectiveness,
                updateSection = updateSection,
                premiumSection = premiumSection,
                guidedTourSection = guidedTourSection,
                guidedTourDestination = guidedTourDestination,
            )
        }
    }
}
