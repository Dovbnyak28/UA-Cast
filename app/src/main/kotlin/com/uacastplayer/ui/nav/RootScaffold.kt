package com.uacastplayer.ui.nav
import com.uacastplayer.ui.theme.UaTheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.backup.BackupExportResult
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.core.nav.BottomNavEvent
import com.uacastplayer.core.nav.BottomNavState
import com.uacastplayer.core.nav.NavBackStackReducer
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.guidedtour.GuidedTourSectionState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.channels.ChannelsScreen
import com.uacastplayer.ui.components.DownloadStatusBanner
import com.uacastplayer.ui.components.UpdateBanner
import com.uacastplayer.ui.components.animationsAllowed
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.ui.components.GlassTabBar
import com.uacastplayer.ui.components.GlassNavigationRail
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.home.HomeContentState
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.settings.SettingsScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.DisplayTitle
import com.uacastplayer.ui.theme.DUR_NAV
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.appBackground
import java.io.File

private val BottomNavStateSaver: Saver<BottomNavState, List<String>> = Saver(
    save = { state -> state.stack.map(BottomDestination::name) },
    restore = { saved -> BottomNavState(saved.map(BottomDestination::valueOf)) },
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
    onEpgSourceSelected: (EpgSource) -> Unit,
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
    updateSection: UpdateSectionState,
    premiumSection: PremiumSectionState,
    guidedTourSection: GuidedTourSectionState,
    modifier: Modifier = Modifier,
    guidedTourDestination: BottomDestination? = null,
) {
    var navState by rememberSaveable(stateSaver = BottomNavStateSaver) { mutableStateOf(BottomNavState()) }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(focusChannelsToken) {
        if (focusChannelsToken > 0) {
            navState = NavBackStackReducer.reduce(navState, BottomNavEvent.Select(BottomDestination.CHANNELS)).state
        }
    }

    // The guided tour asking to be on a particular tab, through the same reducer a tap goes through
    // rather than a second way of changing tabs - so the back stack the user is left with when the
    // tour ends is one this app could have produced on its own. Null for a step that does not care
    // where it is shown, and for all of ordinary use.
    LaunchedEffect(guidedTourDestination) {
        if (guidedTourDestination != null && guidedTourDestination != navState.current) {
            navState = NavBackStackReducer.reduce(navState, BottomNavEvent.Select(guidedTourDestination)).state
        }
    }

    BackHandler {
        val result = NavBackStackReducer.reduce(navState, BottomNavEvent.Back)
        if (result.shouldExitApp) onExitApp() else navState = result.state
    }

    val navigationItems = BottomDestination.entries.map { destination ->
        TabBarItem(
            label = stringResource(destination.tabLabelRes()),
            icon = destination.icon(),
            selected = destination == navState.current,
            tourKey = destination.tourKey(),
            contentDescription = stringResource(destination.labelRes()),
            onClick = {
                navState = NavBackStackReducer.reduce(
                    navState,
                    BottomNavEvent.Select(destination),
                ).state
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().appBackground()) {
        val widthDp = maxWidth.value.toInt()
        val navigationMode = AdaptiveRootLayout.navigationModeFor(widthDp)
        val expanded = AdaptiveRootLayout.isExpanded(widthDp)
        val navAnimationsAllowed = animationsAllowed()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                RootTopBar(
                    // Home already carries the product identity in its content. Keep one
                    // authoritative branded title in the global chrome instead of rendering a
                    // second, competing heading inside HomeScreen.
                    title = if (navState.current == BottomDestination.HOME) {
                        stringResource(R.string.app_name)
                    } else {
                        stringResource(navState.current.labelRes())
                    },
                    iconPrefetchState = iconPrefetchState,
                    epgState = epgState,
                    showDownloadStatus = shouldShowDownloadStatus(playlistState),
                    updateSection = updateSection,
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
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AnimatedContent(
                        targetState = navState.current,
                        transitionSpec = { navigationTransition(navAnimationsAllowed) },
                        label = "adaptiveNavContent",
                        modifier = Modifier.fillMaxSize(),
                    ) { destination ->
                        val contentMaxWidth = if (expanded && destination == BottomDestination.CHANNELS) {
                            1_200.dp
                        } else {
                            840.dp
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            stateHolder.SaveableStateProvider(destination) {
                                val content = Modifier.widthIn(max = contentMaxWidth).fillMaxSize()
                                when (destination) {
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
                        onOpenAddPlaylist = onOpenAddPlaylist,
                        onRefreshPlaylist = onRefreshPlaylist,
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
                )
                BottomDestination.CHANNELS -> ChannelsScreen(
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
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                    currentAppTheme = currentAppTheme,
                    onAppThemeSelected = onAppThemeSelected,
                    currentEpgSource = epgState.selectedSource,
                    onEpgSourceSelected = onEpgSourceSelected,
                    suggestedEpgUrl = epgState.suggestedUrl,
                    epgTruncated = epgState.data?.truncation?.any == true,
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
                    updateSection = updateSection,
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

/**
 * The screen title and background-download/update banners.
 *
 * The banner belongs *here*, as part of the measured top bar, and not in an overlay Box stacked on
 * top of the whole scaffold, which is where it used to live. As an overlay it was simply drawn over
 * whatever was underneath: while a playlist was loading it cut the screen title in half ("UA Cast
 * Player" on Home, the first section header in Settings), which is the exact moment the app most
 * needs to look like it is working rather than broken. As part of the top bar the Scaffold measures
 * it and hands the content the remaining height, so it pushes instead of covering.
 *
 * The status-bar inset is applied once, by the outer Column, and Compose *consumes* it for the
 * subtree - so [DownloadStatusBanner]'s own `statusBarsPadding()` and the title Row (which used to
 * carry the inset itself) both resolve to zero here rather than stacking into a double gap. The
 * background is applied before the inset padding, so it still fills the status bar area.
 *
 * Extracted from the `topBar` lambda so the no-overlap property can be asserted directly; composing
 * the whole of [RootScaffold] in a test would mean supplying sixty-odd parameters. Cast controls
 * intentionally live in the player overlay, where a video stream is active, rather than in this
 * global navigation chrome.
 */
@Composable
internal fun RootTopBar(
    title: String,
    iconPrefetchState: IconPrefetchUiState,
    epgState: EpgUiState,
    showDownloadStatus: Boolean,
    updateSection: UpdateSectionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(UaTheme.palette.void)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        if (showDownloadStatus) {
            DownloadStatusBanner(iconPrefetchState = iconPrefetchState, epgState = epgState)
        }
        UpdateBanner(
            release = updateSection.state.availableRelease,
            installState = updateSection.installState,
            onInstall = updateSection.onDownloadAndInstall,
            onOpen = updateSection.onOpenRelease,
            onDismiss = updateSection.onDismissBanner,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHPadding, vertical = 12.dp)
                .testTag(UiTestTags.ROOT_TOP_BAR_TITLE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DisplayTitle,
                    color = UaTheme.palette.labelPrimary,
                )
            }
        }
    }
}

private fun BottomDestination.labelRes(): Int = when (this) {
    BottomDestination.HOME -> R.string.nav_home
    BottomDestination.CHANNELS -> R.string.nav_channels
    BottomDestination.FAVORITES -> R.string.nav_favorites
    BottomDestination.SETTINGS -> R.string.nav_settings
}

private fun BottomDestination.tabLabelRes(): Int = when (this) {
    BottomDestination.SETTINGS -> R.string.nav_settings_compact
    else -> labelRes()
}

/** Two tabs are tour targets; the other steps point at things inside a screen rather than at the
 * tab that reaches it. Null elsewhere rather than a key nothing uses. */
private fun BottomDestination.tourKey(): String? = when (this) {
    BottomDestination.FAVORITES -> GuidedTourKeys.FAVORITE_BUTTON
    BottomDestination.SETTINGS -> GuidedTourKeys.SETTINGS_BUTTON
    else -> null
}

private fun BottomDestination.icon() = when (this) {
    BottomDestination.HOME -> com.uacastplayer.ui.theme.AppIcons.Home
    BottomDestination.CHANNELS -> com.uacastplayer.ui.theme.AppIcons.Channels
    BottomDestination.FAVORITES -> com.uacastplayer.ui.theme.AppIcons.Favorites
    BottomDestination.SETTINGS -> com.uacastplayer.ui.theme.AppIcons.Settings
}

private fun AnimatedContentTransitionScope<BottomDestination>.navigationTransition(
    animationsAllowed: Boolean,
): ContentTransform {
    if (!animationsAllowed) return EnterTransition.None togetherWith ExitTransition.None

    val movingForward = targetState.ordinal > initialState.ordinal
    val enterOffset = if (movingForward) {
        { width: Int -> width / 10 }
    } else {
        { width: Int -> -width / 10 }
    }
    val exitOffset = if (movingForward) {
        { width: Int -> -width / 10 }
    } else {
        { width: Int -> width / 10 }
    }
    return (slideInHorizontally(
        initialOffsetX = enterOffset,
        animationSpec = tween(DUR_NAV, easing = EaseSpring),
    ) + fadeIn(tween(DUR_NAV, easing = EaseSpring))) togetherWith
        (slideOutHorizontally(
            targetOffsetX = exitOffset,
            animationSpec = tween(DUR_NAV, easing = EaseSpring),
        ) + fadeOut(tween(DUR_NAV, easing = EaseSpring)))
}
