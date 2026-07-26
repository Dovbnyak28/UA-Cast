package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.IconTierBanner
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.ScreenHPadding
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    playlistState: PlaylistUiState,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    iconPrefetchState: IconPrefetchUiState,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onRefreshPlaylist: () -> Unit,
    showIconTierBanner: Boolean,
    onEnableIcons: () -> Unit,
    onDismissIconTierBanner: () -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onClearGroupOverride: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }
    var guideChannel by remember { mutableStateOf<M3uChannel?>(null) }

    // Forces every ChannelIcon/GroupIconCollage in this screen to re-resolve when either signal
    // fires: EPG data arriving unlocks its icon-URL source (see AppViewModel.resolveChannelIcon),
    // and a completed prefetch run may have just written new files for channels that previously
    // resolved to nothing. Deliberately NOT nowMillis or anything else that changes often - a
    // re-resolve on every recomposition would defeat the point of ChannelIcon's own caching.
    val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns

    // Landing on a groups overview (like the rest of the bottom-nav tabs, this is per-tab UI state,
    // not app state - it deliberately resets to the overview on process death, unlike the playlist
    // itself). Storing the group's stable key (not the GroupedChannels/ChannelGroup value) keeps
    // this Saveable and lets the open group re-resolve against a reloaded playlist.
    var openGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openGroup = remember(playlistState.groups, openGroupKey) {
        openGroupKey?.let { key -> playlistState.groups.firstOrNull { groupDisplayKey(it.group) == key } }
    }
    if (openGroupKey != null && openGroup == null) {
        openGroupKey = null
    }
    if (openGroup != null) {
        BackHandler { openGroupKey = null }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = ScreenHPadding)) {
        if (showIconTierBanner) {
            IconTierBanner(
                onEnableIcons = onEnableIcons,
                onDismiss = onDismissIconTierBanner,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (iconPrefetchState.isRunning) {
            val progress = if (iconPrefetchState.total > 0) {
                iconPrefetchState.completed.toFloat() / iconPrefetchState.total
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        // Only a URL-sourced playlist has anything to pull-to-refresh from; a file import would
        // just spin and settle back with nothing having happened, which reads as broken.
        if (playlistState.sourceUrl != null) {
            PullToRefreshBox(
                isRefreshing = playlistState.isLoading && playlistState.hasChannels,
                onRefresh = onRefreshPlaylist,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                ChannelsContent(
                    playlistState = playlistState,
                    openGroup = openGroup,
                    flatChannels = flatChannels,
                    epgState = epgState,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    cachedIconFile = cachedIconFile,
                    density = density,
                    layout = layout,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOpenGroup = { openGroupKey = groupDisplayKey(it.group) },
                    onCloseGroup = { openGroupKey = null },
                    onChannelSelected = onChannelSelected,
                    onLongPressChannel = { guideChannel = it },
                    pinnedGroupKeys = pinnedGroupKeys,
                    hiddenGroupKeys = hiddenGroupKeys,
                    onPinGroup = onPinGroup,
                    onHideGroup = onHideGroup,
                    onClearGroupOverride = onClearGroupOverride,
                )
            }
        } else {
            ChannelsContent(
                playlistState = playlistState,
                openGroup = openGroup,
                flatChannels = flatChannels,
                epgState = epgState,
                iconRefreshKey = iconRefreshKey,
                resolveIcon = resolveIcon,
                cachedIconFile = cachedIconFile,
                density = density,
                layout = layout,
                onChannelLayoutSelected = onChannelLayoutSelected,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenGroup = { openGroupKey = groupDisplayKey(it.group) },
                onCloseGroup = { openGroupKey = null },
                onChannelSelected = onChannelSelected,
                onLongPressChannel = { guideChannel = it },
                pinnedGroupKeys = pinnedGroupKeys,
                hiddenGroupKeys = hiddenGroupKeys,
                onPinGroup = onPinGroup,
                onHideGroup = onHideGroup,
                onClearGroupOverride = onClearGroupOverride,
            )
        }
    }

    guideChannel?.let { channel ->
        com.uacastplayer.ui.epg.EpgGuideSheet(
            channel = channel,
            epgData = epgState.data,
            nowMillis = epgState.nowMillis,
            onDismiss = { guideChannel = null },
        )
    }
}

@Composable
private fun ChannelsContent(
    playlistState: PlaylistUiState,
    openGroup: GroupedChannels?,
    flatChannels: List<M3uChannel>,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onOpenGroup: (GroupedChannels) -> Unit,
    onCloseGroup: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onLongPressChannel: (M3uChannel) -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onClearGroupOverride: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // hasChannels wins over both isLoading and error: a reload in progress or one that
            // just failed must not hide channels already on screen - see applyPlaylistOutcome/
            // PlaylistOutcomeReducer, which deliberately preserve them for exactly this reason.
            playlistState.hasChannels -> {
                val group = openGroup
                if (group == null) {
                    GroupsOverviewGrid(
                        groups = playlistState.groups,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        onGroupClick = onOpenGroup,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        cachedIconFile = cachedIconFile,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onChannelClick = { channel ->
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        },
                        pinnedGroupKeys = pinnedGroupKeys,
                        hiddenGroupKeys = hiddenGroupKeys,
                        onPinGroup = onPinGroup,
                        onHideGroup = onHideGroup,
                        onClearGroupOverride = onClearGroupOverride,
                    )
                } else {
                    SingleGroupChannelList(
                        grouped = group,
                        epgState = epgState,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        density = density,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onBack = onCloseGroup,
                        onChannelClick = { channel ->
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        },
                        onLongPressChannel = onLongPressChannel,
                    )
                }
            }
            playlistState.isLoading -> LoadingState()
            playlistState.error != null -> ErrorState(playlistState.error)
            else -> EmptyState()
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.playlist_loading),
                style = BodyText,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(error: PlaylistError) {
    val message = when (error) {
        PlaylistError.SizeLimitExceeded -> stringResource(R.string.playlist_error_size_limit)
        is PlaylistError.Http -> stringResource(R.string.playlist_error_http, error.code)
        PlaylistError.Network -> stringResource(R.string.playlist_error_network)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = BodyText, color = MaterialTheme.colorScheme.error)
    }
}

/** No playlist loaded at all - unlike [ErrorState] (a load that failed) or the search's
 * [NoSearchResults] (a query with no matches), this dead end needs a way out, but the action
 * itself now lives on Home (see [com.uacastplayer.ui.home.HomeScreen]'s own empty state) rather
 * than being duplicated here. */
@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconHeader(
            icon = AppIcons.Channels,
            title = stringResource(R.string.channels_empty_message),
            subtitle = stringResource(R.string.channels_empty_subtitle),
        )
    }
}

/** Shared with SettingsScreen's hidden-groups list - not just ChannelsScreen's own use. */
@Composable
internal fun groupLabel(group: ChannelGroup): String = when (group) {
    is ChannelGroup.Known -> stringResource(group.labelRes())
    is ChannelGroup.Custom -> group.rawTitle
    ChannelGroup.Ungrouped -> stringResource(R.string.group_ungrouped)
}

private fun ChannelGroup.Known.labelRes(): Int = when (key) {
    ChannelGroup.KEY_MOVIES -> R.string.group_movies
    ChannelGroup.KEY_SERIES -> R.string.group_series
    ChannelGroup.KEY_NEWS -> R.string.group_news
    ChannelGroup.KEY_SPORTS -> R.string.group_sports
    ChannelGroup.KEY_KIDS -> R.string.group_kids
    ChannelGroup.KEY_MUSIC -> R.string.group_music
    ChannelGroup.KEY_DOCUMENTARY -> R.string.group_documentary
    ChannelGroup.KEY_ENTERTAINMENT -> R.string.group_entertainment
    ChannelGroup.KEY_SCIENCE -> R.string.group_science
    ChannelGroup.KEY_RELIGION -> R.string.group_religion
    ChannelGroup.KEY_REGIONAL -> R.string.group_regional
    else -> R.string.group_ungrouped
}
