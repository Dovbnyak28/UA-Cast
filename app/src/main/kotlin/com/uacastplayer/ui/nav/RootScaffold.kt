package com.uacastplayer.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.core.nav.BottomNavEvent
import com.uacastplayer.core.nav.BottomNavState
import com.uacastplayer.core.nav.NavBackStackReducer
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.channels.ChannelsScreen
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.settings.SettingsScreen
import java.io.File

private val BottomNavStateSaver: Saver<BottomNavState, List<String>> = Saver(
    save = { state -> state.stack.map(BottomDestination::name) },
    restore = { saved -> BottomNavState(saved.map(BottomDestination::valueOf)) },
)

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
        bottomBar = {
            NavigationBar {
                BottomDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == navState.current,
                        onClick = {
                            navState = NavBackStackReducer.reduce(
                                navState,
                                BottomNavEvent.Select(destination),
                            ).state
                        },
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes())) },
                    )
                }
            }
        },
    ) { innerPadding ->
        stateHolder.SaveableStateProvider(navState.current) {
            val content = Modifier.padding(innerPadding)
            when (navState.current) {
                BottomDestination.HOME -> HomeScreen(modifier = content)
                BottomDestination.CHANNELS -> ChannelsScreen(
                    playlistState = playlistState,
                    onLoadUrl = onLoadPlaylistUrl,
                    onPickFile = onPickPlaylistFile,
                    onChannelSelected = onChannelSelected,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                    resolveIcon = resolveIcon,
                    modifier = content,
                )
                BottomDestination.FAVORITES -> FavoritesScreen(modifier = content)
                BottomDestination.SETTINGS -> SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                    currentEpgSource = epgState.selectedSource,
                    onEpgSourceSelected = onEpgSourceSelected,
                    iconWifiOnly = iconPrefetchState.wifiOnly,
                    onIconWifiOnlyChanged = onIconWifiOnlyChanged,
                    modifier = content,
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

private fun BottomDestination.icon() = when (this) {
    BottomDestination.HOME -> com.uacastplayer.ui.theme.AppIcons.Home
    BottomDestination.CHANNELS -> com.uacastplayer.ui.theme.AppIcons.Channels
    BottomDestination.FAVORITES -> com.uacastplayer.ui.theme.AppIcons.Favorites
    BottomDestination.SETTINGS -> com.uacastplayer.ui.theme.AppIcons.Settings
}
