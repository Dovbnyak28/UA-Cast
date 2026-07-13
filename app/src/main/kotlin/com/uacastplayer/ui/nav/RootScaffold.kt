package com.uacastplayer.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.cast.CastPlaybackState
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.core.nav.BottomNavEvent
import com.uacastplayer.core.nav.BottomNavState
import com.uacastplayer.core.nav.NavBackStackReducer
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
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
import com.uacastplayer.ui.theme.LargeTitle
import com.uacastplayer.ui.theme.LabelPrimary
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Void
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
    onExitApp: () -> Unit,
    playlistState: PlaylistUiState,
    onLoadPlaylistUrl: (String) -> Unit,
    onPickPlaylistFile: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    onEpgSourceSelected: (EpgSource) -> Unit,
    iconPrefetchState: IconPrefetchUiState,
    onIconWifiOnlyChanged: (Boolean) -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    castState: CastPlaybackState,
    settingsState: SettingsUiState,
    onIconDisplayModeSelected: (IconDisplayMode) -> Unit,
    onListDensitySelected: (ListDensity) -> Unit,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    onWrapAroundChanged: (Boolean) -> Unit,
    onAutoSkipChanged: (Boolean) -> Unit,
    onClearCache: (CacheKind) -> Unit,
    favorites: List<FavoriteChannel>,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onOpenBatteryOptimizationHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var navState by rememberSaveable(stateSaver = BottomNavStateSaver) { mutableStateOf(BottomNavState()) }
    val stateHolder = rememberSaveableStateHolder()

    BackHandler {
        val result = NavBackStackReducer.reduce(navState, BottomNavEvent.Back)
        if (result.shouldExitApp) onExitApp() else navState = result.state
    }

    Scaffold(
        modifier = modifier,
        containerColor = Void,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Void)
                    .padding(horizontal = ScreenHPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(navState.current.labelRes()), style = LargeTitle, color = LabelPrimary)
                    if (castState.isSessionConnected) {
                        Text(
                            text = stringResource(R.string.cast_status_connected),
                            style = com.uacastplayer.ui.theme.Caption,
                            color = com.uacastplayer.ui.theme.Azure,
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
                    onLoadUrl = { url ->
                        navState = NavBackStackReducer.reduce(
                            navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS),
                        ).state
                        onLoadPlaylistUrl(url)
                    },
                    onPickFile = {
                        navState = NavBackStackReducer.reduce(
                            navState,
                            BottomNavEvent.Select(BottomDestination.CHANNELS),
                        ).state
                        onPickPlaylistFile()
                    },
                    modifier = content,
                )
                BottomDestination.CHANNELS -> ChannelsScreen(
                    playlistState = playlistState,
                    onLoadUrl = onLoadPlaylistUrl,
                    onPickFile = onPickPlaylistFile,
                    onChannelSelected = onChannelSelected,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                    resolveIcon = resolveIcon,
                    density = settingsState.listDensity,
                    layout = settingsState.channelLayout,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    modifier = content,
                )
                BottomDestination.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    onChannelSelected = onChannelSelected,
                    onRemove = onRemoveFavorite,
                    modifier = content,
                )
                BottomDestination.SETTINGS -> SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                    currentEpgSource = epgState.selectedSource,
                    onEpgSourceSelected = onEpgSourceSelected,
                    iconWifiOnly = iconPrefetchState.wifiOnly,
                    onIconWifiOnlyChanged = onIconWifiOnlyChanged,
                    settingsState = settingsState,
                    onIconDisplayModeSelected = onIconDisplayModeSelected,
                    onListDensitySelected = onListDensitySelected,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    onWrapAroundChanged = onWrapAroundChanged,
                    onAutoSkipChanged = onAutoSkipChanged,
                    onClearCache = onClearCache,
                    onOpenBatteryOptimizationHint = onOpenBatteryOptimizationHint,
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
