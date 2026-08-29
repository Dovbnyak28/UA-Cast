package com.uacastplayer.ui.home
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.epg.EpgLookup
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.home.HomeContentPolicy
import com.uacastplayer.home.HomeContent
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.GlowStatusDot
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.premium.LocalPremiumNotice
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.NpLogoSize
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

/** Everything HomeScreen needs about the active playlist's own content - grouped so the composable
 * signature doesn't take each of these as a separate top-level parameter (was 15 total; see block
 * 2.4 in the consolidated fix plan). */
data class HomeContentState(
    val playlistState: PlaylistUiState,
    val epgState: EpgUiState,
    val iconPrefetchState: IconPrefetchUiState,
    val favorites: List<FavoriteChannel>,
    val lastWatchedChannelKey: String?,
)

/** Everything HomeScreen needs about the playlist *source* switcher (the active-playlist card's
 * tap target, and the sheet it opens) - see [HomeContentState]. */
data class HomeSourceState(
    val playlistSources: List<PlaylistSource>,
    val activePlaylistSourceId: String?,
    val onSwitchPlaylistSource: (PlaylistSource) -> Unit,
    val onRemovePlaylistSource: (PlaylistSource) -> Unit,
    val onOpenAddPlaylist: () -> Unit,
    val onRefreshPlaylist: () -> Unit,
)

@Composable
fun HomeScreen(
    content: HomeContentState,
    source: HomeSourceState,
    resolveIcon: suspend (M3uChannel) -> File?,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onOpenChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlistState = content.playlistState
    val epgState = content.epgState
    val iconPrefetchState = content.iconPrefetchState
    val favorites = content.favorites
    val lastWatchedChannelKey = content.lastWatchedChannelKey
    val playlistSources = source.playlistSources
    val activePlaylistSourceId = source.activePlaylistSourceId
    val onSwitchPlaylistSource = source.onSwitchPlaylistSource
    val onRemovePlaylistSource = source.onRemovePlaylistSource
    val onOpenAddPlaylist = source.onOpenAddPlaylist
    val onRefreshPlaylist = source.onRefreshPlaylist
    // Same idea as ChannelsScreen's iconRefreshKey - forces the icons below to re-resolve once EPG
    // data arrives or a prefetch run finishes writing new files.
    val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns
    var showSourceSheet by remember { mutableStateOf(false) }
    val flatChannels = playlistState.channels
    val totalChannels = flatChannels.size
    val initialHomeContent = remember(favorites) {
        HomeContent(
            continueWatching = null,
            favorites = favorites.take(HomeContentPolicy.MAX_FAVORITES_SHOWN),
        )
    }
    val homeContent by produceState(
        initialValue = initialHomeContent,
        lastWatchedChannelKey,
        flatChannels,
        favorites,
    ) {
        value = if (lastWatchedChannelKey == null) {
            initialHomeContent
        } else {
            withPlaylistCpu { HomeContentPolicy.resolve(lastWatchedChannelKey, flatChannels, favorites) }
        }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenHPadding),
    ) {
        Text(
            text = stringResource(R.string.home_subtitle),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Draws nothing at all unless a trial is in its last days or has just ended - see
        // UpgradeBanner, which owns that rule. Placed above the dashboard because it is news about
        // the app the user is looking at, and below the title because it is not what Home is for.
        LocalPremiumNotice.current(Modifier.padding(top = 12.dp))

        if (playlistState.hasChannels) {
            PlaylistDashboardCard(
                playlistState = playlistState,
                epgState = epgState,
                totalChannels = totalChannels,
                favoriteCount = favorites.size,
                onRefreshPlaylist = onRefreshPlaylist,
                onClick = { showSourceSheet = true },
            )

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

            PrimaryButton(
                text = stringResource(R.string.home_view_channels_button),
                onClick = onOpenChannels,
                leadingIcon = AppIcons.Channels,
                modifier = Modifier.fillMaxWidth().padding(top = GapL),
            )

            if (favoriteChannels.isNotEmpty()) {
                HomeFavoritesRow(
                    channels = favoriteChannels,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    onClick = { index -> onChannelSelected(favoriteChannels, index) },
                    modifier = Modifier.padding(top = GapL, bottom = GapL),
                )
            }
        } else if (playlistState.isLoading) {
            // hasChannels wins over isLoading, and isLoading over the empty state - the same order
            // ChannelsScreen uses, and for the same reason: a restore in progress is not the
            // absence of a playlist, and saying so is a lie the user has no way to check. Without
            // this branch a cold start showed "no playlist yet" plus an add-a-playlist button for
            // the whole restore, which on a debug build over a 2863-channel snapshot is ~30s.
            HomeDashboardSkeleton()
        } else {
            HomeNoChannelsState(playlistState, onRefreshPlaylist, onOpenAddPlaylist)
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

@Composable
private fun HomeNoChannelsState(
    playlistState: PlaylistUiState,
    onRefreshPlaylist: () -> Unit,
    onOpenAddPlaylist: () -> Unit,
) {
    val error = playlistState.error
    val errorMessage = when (error) {
        PlaylistError.SizeLimitExceeded -> stringResource(R.string.playlist_error_size_limit)
        is PlaylistError.Http -> stringResource(R.string.playlist_error_http, error.code)
        PlaylistError.Network -> stringResource(R.string.playlist_error_network)
        PlaylistError.Empty -> stringResource(R.string.playlist_error_empty)
        null -> null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = GapL),
    ) {
        IconHeader(
            icon = if (errorMessage == null) AppIcons.Upload else AppIcons.HelpCircle,
            title = errorMessage ?: stringResource(R.string.home_empty_message),
            subtitle = stringResource(
                if (errorMessage == null) R.string.home_empty_subtitle else R.string.channels_error_subtitle,
            ),
        )
        val retryExisting = errorMessage != null && playlistState.sourceUrl != null
        PrimaryButton(
            text = stringResource(
                if (retryExisting) R.string.common_retry else R.string.home_add_playlist_button,
            ),
            onClick = if (retryExisting) onRefreshPlaylist else onOpenAddPlaylist,
            leadingIcon = if (retryExisting) AppIcons.Refresh else AppIcons.Upload,
            modifier = Modifier
                .padding(top = GapM)
                .guidedTourTarget(GuidedTourKeys.PLAYLIST_ADD),
        )
    }
}

/**
 * The active-playlist card: which playlist is loaded, its three counters, and the EPG's state.
 *
 * Extracted from [HomeScreen] rather than left inline - the screen was at its complexity ceiling,
 * and this is the largest self-contained piece of it. [HomeDashboardSkeleton] mirrors these
 * proportions.
 */
@Composable
private fun PlaylistDashboardCard(
    playlistState: PlaylistUiState,
    epgState: EpgUiState,
    totalChannels: Int,
    favoriteCount: Int,
    onRefreshPlaylist: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GapL)
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .clickable(onClick = onClick)
            // The tour's "add a playlist" target for a user who already has one: this card is what
            // opens the source sheet, and adding another is what that sheet is for. The empty-state
            // button below registers the same name - the two branches are mutually exclusive, so
            // only one of them is ever live.
            .guidedTourTarget(GuidedTourKeys.PLAYLIST_ADD)
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
                    text = playlistState.displayName ?: stringResource(R.string.playlist_unnamed),
                    // A source-derived label can still look URL-like (host/file). It identifies
                    // the playlist, but should not visually outrank the app title or the stats the
                    // user came here for.
                    style = CardTitle,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                label = pluralStringResource(R.plurals.home_stat_channels, totalChannels),
                icon = AppIcons.Tv,
                modifier = Modifier.weight(1f),
            )
            HomeStatCell(
                count = playlistState.groups.size,
                label = pluralStringResource(R.plurals.home_stat_groups, playlistState.groups.size),
                icon = AppIcons.Channels,
                modifier = Modifier.weight(1f),
            )
            HomeStatCell(
                count = favoriteCount,
                label = pluralStringResource(R.plurals.home_stat_favorites, favoriteCount),
                icon = AppIcons.Favorites,
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
}

/** Re-fetches the active playlist from its saved URL - see [PlaylistUiState.sourceUrl]. Swaps to a
 * spinner instead of disabling outright while a load is already in flight, so the user can see
 * their tap registered rather than wondering if the button is just unresponsive. */
@Composable
private fun RefreshPlaylistButton(isLoading: Boolean, onClick: () -> Unit) {
    // The label sits on the button rather than on the icon, because the icon is only there in one of
    // the two states: while loading it is a spinner, and a spinner carries no description - which
    // left this control announcing itself to TalkBack as an unnamed disabled button for exactly as
    // long as the refresh it was reporting.
    val label = stringResource(R.string.home_refresh_playlist)
    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = UaTheme.palette.azure,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(AppIcons.Refresh, contentDescription = null, tint = UaTheme.palette.azure)
        }
    }
}

@Composable
private fun HomeStatCell(
    count: Int,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = count.toString(), style = Title, color = UaTheme.palette.labelPrimary)
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
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
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
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
            // Centered instead of packed to the start: with just a couple of favorites (the
            // common case right after someone starts using this feature) a left-packed row left a
            // large, lopsided gap on the right. Once there are enough favorites to fill/overflow
            // the screen, this has no visible effect - there's nothing left to center once the row
            // is already scrolling, so it looks identical to before at higher counts.
            horizontalArrangement = Arrangement.spacedBy(GapM, Alignment.CenterHorizontally),
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
