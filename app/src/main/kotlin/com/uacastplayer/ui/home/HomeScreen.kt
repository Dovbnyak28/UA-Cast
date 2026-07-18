package com.uacastplayer.ui.home
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.EpgLookup
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.home.HomeContentPolicy
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.GlowStatusDot
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.DisplayTitle
import com.uacastplayer.ui.theme.NpLogoSize
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.Title
import java.io.File

@Composable
fun HomeScreen(
    playlistState: PlaylistUiState,
    epgState: EpgUiState,
    iconPrefetchState: IconPrefetchUiState,
    favorites: List<FavoriteChannel>,
    lastWatchedChannelKey: String?,
    resolveIcon: suspend (M3uChannel) -> File?,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onOpenChannels: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    playlistSources: List<PlaylistSource>,
    activePlaylistSourceId: String?,
    onSwitchPlaylistSource: (PlaylistSource) -> Unit,
    onRemovePlaylistSource: (PlaylistSource) -> Unit,
    onOpenAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same idea as ChannelsScreen's iconRefreshKey - forces the icons below to re-resolve once EPG
    // data arrives or a prefetch run finishes writing new files.
    val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns
    var showSourceSheet by remember { mutableStateOf(false) }
    val totalChannels = playlistState.groups.sumOf { it.channels.size }
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }
    val homeContent = remember(lastWatchedChannelKey, flatChannels, favorites) {
        HomeContentPolicy.resolve(lastWatchedChannelKey, flatChannels, favorites)
    }
    val favoriteChannels = remember(homeContent.favorites) {
        homeContent.favorites.map { fav ->
            M3uChannel(
                displayName = fav.displayName,
                streamUrl = fav.streamUrl,
                tvgId = fav.tvgId,
                groupTitle = fav.groupTitle,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenHPadding)) {
        Text(
            text = stringResource(R.string.app_name),
            style = DisplayTitle,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (playlistState.hasChannels) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GapL)
                    .clip(RoundedCornerShape(RadiusCard))
                    .background(UaTheme.palette.surface1)
                    .border(1.dp, UaTheme.palette.hairline, RoundedCornerShape(RadiusCard))
                    .clickable { showSourceSheet = true }
                    .padding(CardPadding),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_active_playlist_label),
                            style = SectionLabel,
                            color = UaTheme.palette.labelSecondary,
                        )
                        Text(
                            text = playlistState.displayName ?: playlistState.activePlaylistId ?: "—",
                            style = Title,
                            color = UaTheme.palette.labelPrimary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    // Only a file import has no sourceUrl to re-fetch - nothing to refresh from.
                    if (playlistState.sourceUrl != null) {
                        RefreshPlaylistButton(isLoading = playlistState.isLoading, onClick = onRefreshPlaylist)
                    }
                }
                if (playlistState.restoredFromCache) {
                    Text(
                        text = stringResource(R.string.home_restored_from_cache),
                        style = Caption,
                        color = UaTheme.palette.labelSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = GapM)) {
                    HomeStatCell(
                        count = totalChannels,
                        label = stringResource(R.string.home_stat_channels),
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatCell(
                        count = playlistState.groups.size,
                        label = stringResource(R.string.home_stat_groups),
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatCell(
                        count = favorites.size,
                        label = stringResource(R.string.home_stat_favorites),
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(modifier = Modifier.padding(top = GapM)) {
                    Text(
                        text = stringResource(R.string.home_epg_label),
                        style = SectionLabel,
                        color = UaTheme.palette.labelSecondary,
                    )
                    Text(
                        text = when {
                            epgState.isLoading -> stringResource(R.string.home_epg_restoring)
                            epgState.data != null -> stringResource(R.string.home_epg_ready)
                            epgState.hasError -> stringResource(R.string.home_epg_unavailable)
                            else -> stringResource(R.string.home_epg_restoring)
                        },
                        style = BodyText,
                        color = UaTheme.palette.labelPrimary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            homeContent.continueWatching?.let { channel ->
                ContinueWatchingCard(
                    channel = channel,
                    epgState = epgState,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    onClick = {
                        val index = flatChannels.indexOf(channel)
                        if (index >= 0) onChannelSelected(flatChannels, index)
                    },
                    modifier = Modifier.padding(top = GapL),
                )
            }

            Button(onClick = onOpenChannels, modifier = Modifier.fillMaxWidth().padding(top = GapL)) {
                Text(stringResource(R.string.home_view_channels_button))
            }

            if (favoriteChannels.isNotEmpty()) {
                HomeFavoritesRow(
                    channels = favoriteChannels,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    onClick = { index -> onChannelSelected(favoriteChannels, index) },
                    modifier = Modifier.padding(top = GapL, bottom = GapL),
                )
            }
        } else {
            IconHeader(
                icon = AppIcons.Upload,
                title = stringResource(R.string.home_empty_message),
                subtitle = stringResource(R.string.home_empty_subtitle),
                modifier = Modifier.fillMaxWidth().padding(top = GapL, bottom = GapL),
            )
        }
    }

    if (showSourceSheet) {
        PlaylistSourceSheet(
            sources = playlistSources,
            activeId = activePlaylistSourceId,
            onSelect = {
                onSwitchPlaylistSource(it)
                showSourceSheet = false
            },
            onRemove = onRemovePlaylistSource,
            onAddNew = {
                showSourceSheet = false
                onOpenAddPlaylist()
            },
            onDismiss = { showSourceSheet = false },
        )
    }
}

/** Re-fetches the active playlist from its saved URL - see [PlaylistUiState.sourceUrl]. Swaps to a
 * spinner instead of disabling outright while a load is already in flight, so the user can see
 * their tap registered rather than wondering if the button is just unresponsive. */
@Composable
private fun RefreshPlaylistButton(isLoading: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !isLoading) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = UaTheme.palette.azure,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                AppIcons.Refresh,
                contentDescription = stringResource(R.string.home_refresh_playlist),
                tint = UaTheme.palette.azure,
            )
        }
    }
}

@Composable
private fun HomeStatCell(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = count.toString(), style = Title, color = UaTheme.palette.labelPrimary)
        Text(
            text = label,
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The last channel played, still present in the current playlist (see [HomeContentPolicy]) - a
 * shortcut back into it without going through Channels, styled like a channel row with its live
 * EPG programme underneath when one is available. */
@Composable
private fun ContinueWatchingCard(
    channel: M3uChannel,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusCard))
            .background(UaTheme.palette.surface1)
            .border(1.dp, UaTheme.palette.hairline, RoundedCornerShape(RadiusCard))
            .clickable(onClick = onClick)
            .padding(CardPadding),
    ) {
        Text(
            text = stringResource(R.string.home_continue_watching_label),
            style = SectionLabel,
            color = UaTheme.palette.labelSecondary,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = GapM), verticalAlignment = Alignment.CenterVertically) {
            ChannelIcon(channel, resolveIcon, refreshKey = iconRefreshKey)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = channel.displayName,
                    style = BodyText.copy(fontFamily = UaTheme.palette.displayFontFamily),
                    color = UaTheme.palette.labelPrimary,
                    maxLines = 1,
                )
                val programme = remember(channel.streamUrl, epgState.data, epgState.nowMillis) {
                    epgState.data?.let { EpgLookup.currentAndNext(it, channel, epgState.nowMillis) }
                }
                val current = programme?.current
                val effectiveStop = programme?.effectiveStopMillis
                if (current != null && effectiveStop != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        GlowStatusDot(variant = StatusPillVariant.Bad, size = 6.dp)
                        Text(
                            text = current.title,
                            style = Caption,
                            color = UaTheme.palette.labelSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    TrackProgress(
                        progress = ProgrammeProgress.progress(current.startMillis, effectiveStop, epgState.nowMillis),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Up to [com.uacastplayer.home.HomeContentPolicy.MAX_FAVORITES_SHOWN] favorites as a horizontal
 * shelf - hidden entirely when there are none, rather than showing an empty-state placeholder for
 * a section the user hasn't used yet. */
@Composable
private fun HomeFavoritesRow(
    channels: List<M3uChannel>,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = SectionLabel,
            color = UaTheme.palette.labelSecondary,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
            horizontalArrangement = Arrangement.spacedBy(GapM),
        ) {
            itemsIndexed(channels) { index, channel ->
                HomeFavoriteItem(
                    channel = channel,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    onClick = { onClick(index) },
                )
            }
        }
    }
}

@Composable
private fun HomeFavoriteItem(
    channel: M3uChannel,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(NpLogoSize + GapM).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelIcon(channel, resolveIcon, size = NpLogoSize, refreshKey = iconRefreshKey)
        Text(
            text = channel.displayName,
            style = Caption,
            color = UaTheme.palette.labelPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = GapS),
        )
    }
}
