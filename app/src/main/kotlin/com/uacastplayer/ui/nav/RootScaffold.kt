package com.uacastplayer.ui.nav
import com.uacastplayer.ui.theme.UaTheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.backup.BackupImportSummary
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.core.nav.BottomNavEvent
import com.uacastplayer.core.nav.BottomNavState
import com.uacastplayer.core.nav.NavBackStackReducer
import com.uacastplayer.data.prefs.BufferSize
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.settings.CacheKind
import com.uacastplayer.settings.SettingsUiState
import com.uacastplayer.ui.cast.CastButton
import com.uacastplayer.ui.channels.ChannelsScreen
import com.uacastplayer.ui.components.GlassTabBar
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.settings.SettingsScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.DisplayTitle
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
    focusChannelsToken: Int,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    onEpgSourceSelected: (EpgSource) -> Unit,
    onUseSuggestedEpgUrl: () -> Unit,
    iconPrefetchState: IconPrefetchUiState,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    castState: CastPlaybackState,
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
    modifier: Modifier = Modifier,
) {
    var navState by rememberSaveable(stateSaver = BottomNavStateSaver) { mutableStateOf(BottomNavState()) }
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

    Scaffold(
        modifier = modifier.fillMaxSize().appBackground(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UaTheme.palette.void)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(navState.current.labelRes()),
                        style = DisplayTitle,
                        color = UaTheme.palette.labelPrimary,
                    )
                    if (castState.isSessionConnected) {
                        Text(
                            text = stringResource(R.string.cast_status_connected),
                            style = com.uacastplayer.ui.theme.Caption,
                            color = UaTheme.palette.accentText,
                        )
                    }
                }
                CastButton()
            }
        },
        bottomBar = {
            GlassTabBar(
                items = BottomDestination.entries.map { destination ->
                    TabBarItem(
                        label = stringResource(destination.labelRes()),
                        icon = destination.icon(),
                        selected = destination == navState.current,
                        onClick = {
                            navState = NavBackStackReducer.reduce(
                                navState,
                                BottomNavEvent.Select(destination),
                            ).state
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Crossfade(targetState = navState.current, label = "bottomNavContent") { destination ->
        stateHolder.SaveableStateProvider(destination) {
            val content = Modifier.padding(innerPadding)
            when (destination) {
                BottomDestination.HOME -> HomeScreen(
                    playlistState = playlistState,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                    favorites = favorites,
                    lastWatchedChannelKey = lastWatchedChannelKey,
                    resolveIcon = resolveIcon,
                    onChannelSelected = onChannelSelected,
                    onOpenChannels = {
                        navState = NavBackStackReducer.reduce(
                            navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS),
                        ).state
                    },
                    onRefreshPlaylist = onRefreshPlaylist,
                    playlistSources = playlistSources,
                    activePlaylistSourceId = activePlaylistSourceId,
                    onSwitchPlaylistSource = onSwitchPlaylistSource,
                    onRemovePlaylistSource = onRemovePlaylistSource,
                    onOpenAddPlaylist = onOpenAddPlaylist,
                    modifier = content,
                )
                BottomDestination.CHANNELS -> ChannelsScreen(
                    playlistState = playlistState,
                    onChannelSelected = onChannelSelected,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                    resolveIcon = resolveIcon,
                    cachedIconFile = cachedIconFile,
                    density = settingsState.listDensity,
                    layout = settingsState.channelLayout,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onRefreshPlaylist = onRefreshPlaylist,
                    onOpenAddPlaylist = onOpenAddPlaylist,
                    showIconTierBanner = settingsState.showIconTierBanner,
                    onEnableIcons = { onIconDisplayModeSelected(IconDisplayMode.CACHE_LIMITED) },
                    onDismissIconTierBanner = onDismissIconTierBanner,
                    modifier = content,
                )
                BottomDestination.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    playlistChannels = playlistState.groups.flatMap { it.channels },
                    sortOrder = settingsState.favoritesSortOrder,
                    onSortOrderSelected = onFavoritesSortOrderSelected,
                    onChannelSelected = onChannelSelected,
                    onRemove = onRemoveFavorite,
                    onReorder = onReorderFavorites,
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
                    backupImportSummary = backupImportSummary,
                    onDismissBackupImportSummary = onDismissBackupImportSummary,
                    onOpenBatteryOptimizationHint = onOpenBatteryOptimizationHint,
                    onAddIconSource = onAddIconSource,
                    onRemoveIconSource = onRemoveIconSource,
                    onDismissIconSourceError = onDismissIconSourceError,
                    onOpenHelp = onOpenHelp,
                    onOpenTerms = onOpenTerms,
                    playlistState = playlistState,
                    onOpenAddPlaylist = onOpenAddPlaylist,
                    modifier = content,
                )
            }
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

private fun BottomDestination.icon() = when (this) {
    BottomDestination.HOME -> com.uacastplayer.ui.theme.AppIcons.Home
    BottomDestination.CHANNELS -> com.uacastplayer.ui.theme.AppIcons.Channels
    BottomDestination.FAVORITES -> com.uacastplayer.ui.theme.AppIcons.Favorites
    BottomDestination.SETTINGS -> com.uacastplayer.ui.theme.AppIcons.Settings
}
