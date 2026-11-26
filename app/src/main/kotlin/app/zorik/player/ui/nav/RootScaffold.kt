package app.zorik.player.ui.nav
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.zorik.player.R
import app.zorik.player.backup.BackupImportSummary
import app.zorik.player.backup.BackupExportResult
import app.zorik.player.core.i18n.AppLanguage
import app.zorik.player.core.nav.BottomDestination
import app.zorik.player.core.nav.BottomNavEvent
import app.zorik.player.core.nav.BottomNavState
import app.zorik.player.core.nav.NavBackStackReducer
import app.zorik.player.core.settings.BufferSize
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.favorites.FavoritesSortOrder
import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.diagnostics.RemuxEffectivenessCounts
import app.zorik.player.epg.EpgUiState
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.guidedtour.GuidedTourSectionState
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistSource
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.settings.CacheKind
import app.zorik.player.settings.SettingsUiState
import app.zorik.player.ui.channels.ChannelsScreen
import app.zorik.player.ui.components.animationsAllowed
import app.zorik.player.premium.PremiumSectionState
import app.zorik.player.ui.components.GlassTabBar
import app.zorik.player.ui.components.GlassNavigationRail
import app.zorik.player.ui.components.TabBarItem
import app.zorik.player.ui.favorites.FavoritesScreen
import app.zorik.player.ui.home.HomeContentState
import app.zorik.player.ui.home.HomeScreen
import app.zorik.player.ui.home.HomeSourceState
import app.zorik.player.ui.settings.SettingsScreen
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.tv.LocalTvMode
import app.zorik.player.ui.tv.TvBrowseScreen
import app.zorik.player.ui.theme.appBackground
import app.zorik.player.ui.theme.miniPlayerContentPadding
import java.io.File


private val BottomNavStateSaver: Saver<BottomNavState, List<String>> = Saver(
    save = { state -> state.stack.map(BottomDestination::name) },
    restore = { saved ->
        val destination = saved.lastOrNull()?.let { id -> BottomDestination.entries.firstOrNull { it.name == id } }
            ?: BottomDestination.START
        NavBackStackReducer.reduce(BottomNavState(), BottomNavEvent.Select(destination)).state
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScaffold(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    currentAppTheme: AppTheme,
    onAppThemeSelected: (AppTheme) -> Unit,
    onExitApp: () -> Unit,
    playlistState: PlaylistUiState,
    onOpenAddPlaylist: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    playlistSources: List<PlaylistSource>,
    activePlaylistSourceId: String?,
    onSwitchPlaylistSource: (PlaylistSource) -> Unit,
    onRemovePlaylistSource: (PlaylistSource) -> Unit,
    onRetrySourceSave: () -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onRestoreGroup: (String) -> Unit,
    isChannelLocked: (M3uChannel) -> Boolean,
    onLockChannel: (M3uChannel) -> Unit,
    onUnlockChannel: (M3uChannel) -> Unit,
    lockedChannelKeys: Set<String>,
    parentalControlPinSet: Boolean,
    onSetParentalControlPin: suspend (String) -> Boolean,
    onResetParentalControl: () -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
    focusChannelsToken: Int,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    onEpgUrlChanged: (String) -> Unit,
    onUseSuggestedEpgUrl: () -> Unit,
    iconPrefetchState: IconPrefetchUiState,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    settingsState: SettingsUiState,
    onIconDisplayModeSelected: (IconDisplayMode) -> Unit,
    onDismissIconTierBanner: () -> Unit,
    onListDensitySelected: (ListDensity) -> Unit,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    onBufferSizeSelected: (BufferSize) -> Unit,
    onFavoritesSortOrderSelected: (FavoritesSortOrder) -> Unit,
    onWrapAroundChanged: (Boolean) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onClearCache: (CacheKind) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupExportResult: BackupExportResult?,
    onDismissBackupExportResult: () -> Unit,
    backupImportSummary: BackupImportSummary?,
    onDismissBackupImportSummary: () -> Unit,
    favorites: List<FavoriteChannel>,
    lastWatchedChannelKey: String?,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onReorderFavorites: (List<FavoriteChannel>) -> Unit,
    onOpenBatteryOptimizationHint: () -> Unit,
    onAddIconSource: (String) -> Unit,
    onRemoveIconSource: (String) -> Unit,
    onDismissIconSourceError: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onBuildDiagnosticsReport: () -> String,
    remuxEffectiveness: RemuxEffectivenessCounts,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
    miniPlayerVisible: Boolean = false,
) {
    var navState by rememberSaveable(stateSaver = BottomNavStateSaver) { mutableStateOf(BottomNavState()) }
    var openEpgSettings by rememberSaveable { mutableStateOf(false) }
    var requestedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(focusChannelsToken) {
        if (focusChannelsToken > 0) {
            navState = NavBackStackReducer.reduce(navState, BottomNavEvent.Select(BottomDestination.CHANNELS)).state
        }
    }

    BackHandler {
        val result = NavBackStackReducer.reduce(navState, BottomNavEvent.Back)
        if (result.shouldExitApp) onExitApp() else navState = result.state
    }

    val navigationItems = BottomDestination.entries.map { destination ->
        TabBarItem(
            label = stringResource(destination.tabLabelRes()),
            largeTextLabel = stringResource(destination.largeTextLabelRes()),
            icon = destination.icon(),
            selected = destination == navState.current,
            contentDescription = stringResource(destination.labelRes()),
            onClick = {
                navState = NavBackStackReducer.reduce(
                    navState,
                    BottomNavEvent.Select(destination),
                ).state
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().appBackground(plain = navState.current == BottomDestination.SETTINGS),
    ) {
        val widthDp = maxWidth.value.toInt()
        val television = LocalTvMode.current
        val navigationMode = rootNavigationMode(widthDp, television)
        val expanded = AdaptiveRootLayout.isExpanded(widthDp)
        val navAnimationsAllowed = animationsAllowed()
        Scaffold(
            modifier = Modifier.fillMaxSize().tvOverscanPadding(television),
            containerColor = Color.Transparent,
            snackbarHost = {
                app.zorik.player.ui.favorites.FavoriteUndoSnackbar(
                    Modifier.padding(bottom = miniPlayerContentPadding(miniPlayerVisible,
                        LocalDensity.current.fontScale)),
                )
            },
            topBar = {
                RootTopBar(
                    // Home already carries the product identity in its content. Keep one
                    // authoritative branded title in the global chrome instead of rendering a
                    // second, competing heading inside HomeScreen.
                    title = if (navState.current == BottomDestination.HOME) {
                        stringResource(R.string.app_brand_short)
                    } else {
                        stringResource(navState.current.labelRes())
                    },
                    iconPrefetchState = iconPrefetchState,
                    epgState = epgState,
                    showDownloadStatus = shouldShowDownloadStatus(playlistState),
                    showTitle = navState.current != BottomDestination.SETTINGS,
                    labelRemote = homeRemoteLabelVisible(navState.current == BottomDestination.HOME,
                        widthDp, LocalDensity.current.fontScale),
                )
            },
            bottomBar = {
                if (navigationMode == RootNavigationMode.BOTTOM_BAR) {
                    GlassTabBar(items = navigationItems)
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (navigationMode == RootNavigationMode.NAVIGATION_RAIL) {
                    GlassNavigationRail(items = navigationItems)
                }
                // Only the content makes room for the mini player: beside a rail the bar now sits to
                // the rail's right (see PlayerZone), so the rail keeps its full height.
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .padding(bottom = miniPlayerContentPadding(miniPlayerVisible, LocalDensity.current.fontScale)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AnimatedContent(
                        targetState = navState.current,
                        transitionSpec = { navigationTransition(navAnimationsAllowed) },
                        label = "adaptiveNavContent",
                        modifier = Modifier.fillMaxSize(),
                    ) { destination ->
                        val contentMaxWidth = rootContentWidth(expanded, destination)
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            stateHolder.SaveableStateProvider(destination) {
                                val content = Modifier.widthIn(max = contentMaxWidth).fillMaxSize()
                                if (television && destination != BottomDestination.SETTINGS) {
                                    TvBrowseScreen(destination, playlistState,
                                        HomeSourceState(playlistSources, activePlaylistSourceId,
                                            onSwitchPlaylistSource, onRemovePlaylistSource, onOpenAddPlaylist,
                                            onRefreshPlaylist, onRetrySourceSave),
                                        favorites, hiddenGroupKeys, resolveIcon, onChannelSelected, content,
                                        iconPrefetchState.refreshKey,
                                        settingsState.favoritesSortOrder)
                                } else when (destination) {
                BottomDestination.HOME -> HomeScreen(
                    content = HomeContentState(
                        playlistState = playlistState,
                        epgState = epgState,
                        iconPrefetchState = iconPrefetchState,
                        favorites = favorites,
                        lastWatchedChannelKey = lastWatchedChannelKey,
                    ),
                    source = HomeSourceState(
                        playlistSources = playlistSources,
                        activePlaylistSourceId = activePlaylistSourceId,
                        onSwitchPlaylistSource = onSwitchPlaylistSource,
                        onRemovePlaylistSource = onRemovePlaylistSource,
                        onRetrySourceSave = onRetrySourceSave,
                        onOpenAddPlaylist = onOpenAddPlaylist,
                        onRefreshPlaylist = onRefreshPlaylist,
                        onRetryEpg = { epgState.customUrl?.let(onEpgUrlChanged) },
                        onOpenEpgSettings = {
                            openEpgSettings = true
                            navState = NavBackStackReducer.reduce(
                                navState, BottomNavEvent.Select(BottomDestination.SETTINGS),
                            ).state
                        },
                    ),
                    resolveIcon = resolveIcon,
                    onChannelSelected = onChannelSelected,
                    onOpenChannels = {
                        navState = NavBackStackReducer.reduce(
                            navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS),
                        ).state
                    },
                    modifier = content,
                    onOpenFavorites = {
                        navState = NavBackStackReducer.reduce(navState,
                            BottomNavEvent.Select(BottomDestination.FAVORITES)).state
                    },
                    onOpenCategory = { key ->
                        requestedGroupKey = key
                        navState = NavBackStackReducer.reduce(navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS)).state
                    },
                    hiddenGroupKeys = hiddenGroupKeys,
                )
                BottomDestination.CHANNELS -> ChannelsScreen(
                    requestedGroupKey = requestedGroupKey,
                    onGroupRequestHandled = { requestedGroupKey = null },
                    playlistState = playlistState,
                    onChannelSelected = onChannelSelected,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                    resolveIcon = resolveIcon,
                    density = settingsState.listDensity,
                    layout = settingsState.channelLayout,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    isChannelLocked = isChannelLocked,
                    onLockChannel = onLockChannel,
                    onUnlockChannel = onUnlockChannel,
                    onRefreshPlaylist = onRefreshPlaylist,
                    showIconTierBanner = settingsState.showIconTierBanner,
                    onEnableIcons = { onIconDisplayModeSelected(IconDisplayMode.CACHE_LIMITED) },
                    onDismissIconTierBanner = onDismissIconTierBanner,
                    pinnedGroupKeys = pinnedGroupKeys,
                    hiddenGroupKeys = hiddenGroupKeys,
                    onPinGroup = onPinGroup,
                    onHideGroup = onHideGroup,
                    onClearGroupOverride = onRestoreGroup,
                    onOpenAddPlaylist = onOpenAddPlaylist,
                    modifier = content,
                )
                BottomDestination.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    iconRefreshKey = iconPrefetchState.refreshKey,
                    playlistChannels = playlistState.channels,
                    sortOrder = settingsState.favoritesSortOrder,
                    onSortOrderSelected = onFavoritesSortOrderSelected,
                    onChannelSelected = onChannelSelected,
                    onRemove = onRemoveFavorite,
                    onReorder = onReorderFavorites,
                    resolveIcon = resolveIcon,
                    onOpenChannels = {
                        navState = NavBackStackReducer.reduce(
                            navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS),
                        ).state
                    },
                    modifier = content,
                )
                BottomDestination.SETTINGS -> SettingsScreen(
                    openEpgSettings = openEpgSettings,
                    onEpgSettingsOpened = { openEpgSettings = false },
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                    currentAppTheme = currentAppTheme,
                    onAppThemeSelected = onAppThemeSelected,
                    epgState = epgState,
                    onEpgUrlChanged = onEpgUrlChanged,
                    onUseSuggestedEpgUrl = onUseSuggestedEpgUrl,
                    iconWifiOnly = iconPrefetchState.wifiOnly,
                    onIconWifiOnlyChanged = onIconWifiOnlyChanged,
                    settingsState = settingsState,
                    onIconDisplayModeSelected = onIconDisplayModeSelected,
                    onListDensitySelected = onListDensitySelected,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    onBufferSizeSelected = onBufferSizeSelected,
                    onWrapAroundChanged = onWrapAroundChanged,
                    onAutoSkipChanged = onAutoSkipChanged,
                    onClearCache = onClearCache,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    backupExportResult = backupExportResult,
                    onDismissBackupExportResult = onDismissBackupExportResult,
                    backupImportSummary = backupImportSummary,
                    onDismissBackupImportSummary = onDismissBackupImportSummary,
                    onOpenBatteryOptimizationHint = onOpenBatteryOptimizationHint,
                    onAddIconSource = onAddIconSource,
                    onRemoveIconSource = onRemoveIconSource,
                    onDismissIconSourceError = onDismissIconSourceError,
                    onOpenHelp = onOpenHelp,
                    onOpenTerms = onOpenTerms,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onBuildDiagnosticsReport = onBuildDiagnosticsReport,
                    remuxEffectiveness = remuxEffectiveness,
                    premiumSection = premiumSection,
                    guidedTourSection = guidedTourSection,
                    playlistState = playlistState,
                    onOpenAddPlaylist = onOpenAddPlaylist,
                    hiddenGroupKeys = hiddenGroupKeys,
                    onRestoreGroup = onRestoreGroup,
                    lockedChannelKeys = lockedChannelKeys,
                    parentalControlPinSet = parentalControlPinSet,
                    onSetParentalControlPin = onSetParentalControlPin,
                    onResetParentalControl = onResetParentalControl,
                    onUnlockChannel = onUnlockChannel,
                    requireParentalControlUnlock = requireParentalControlUnlock,
                    modifier = content,
                )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldShowDownloadStatus(playlistState: PlaylistUiState): Boolean =
    playlistState.hasChannels || playlistState.isLoading

private fun rootNavigationMode(widthDp: Int, television: Boolean): RootNavigationMode =
    if (television) RootNavigationMode.NAVIGATION_RAIL else AdaptiveRootLayout.navigationModeFor(widthDp)

private fun Modifier.tvOverscanPadding(television: Boolean): Modifier =
    if (television) padding(24.dp) else this

private fun rootContentWidth(expanded: Boolean, destination: BottomDestination) =
    if (expanded && destination == BottomDestination.CHANNELS) 1_200.dp else 840.dp
