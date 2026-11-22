package app.zorik.player.ui.home
import app.zorik.player.ui.theme.UaTheme

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import app.zorik.player.ui.components.GradientPlayButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.ads.AdPlacement
import app.zorik.player.ui.ads.BannerAdSlot
import app.zorik.player.data.playlist.resolveHomeContent
import app.zorik.player.guidedtour.GuidedTourKeys
import app.zorik.player.ui.guidedtour.guidedTourTarget
import app.zorik.player.epg.EpgLookup
import app.zorik.player.epg.EpgUiState
import app.zorik.player.epg.ProgrammeProgress
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.home.HomeContentPolicy
import app.zorik.player.home.HomeContent
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistSource
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.ui.playlist.asUserMessage
import app.zorik.player.ui.components.ChannelIcon
import app.zorik.player.ui.components.GlowStatusDot
import app.zorik.player.ui.components.IconHeader
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.components.StatusPillVariant
import app.zorik.player.ui.components.TrackProgress
import app.zorik.player.ui.premium.LocalPremiumNotice
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.CardPadding
import app.zorik.player.ui.theme.CardTitle
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.GapL
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.GapS
import app.zorik.player.ui.theme.NpLogoSize
import app.zorik.player.ui.theme.RadiusCard
import app.zorik.player.ui.theme.SectionLabel
import app.zorik.player.ui.theme.panelSurface
import app.zorik.player.ui.theme.raisedSurface
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
    val onRetrySourceSave: () -> Unit = {},
    val onOpenEpgSettings: () -> Unit = {},
    val onRetryEpg: () -> Unit = {},
)

@Composable
fun HomeScreen(
    content: HomeContentState,
    source: HomeSourceState,
    resolveIcon: suspend (M3uChannel) -> File?,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onOpenChannels: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFavorites: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    hiddenGroupKeys: Set<String> = emptySet(),
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
    // Refresh on user pack edits or completed downloads, not unrelated EPG updates.
    val iconRefreshKey: Any = iconPrefetchState.refreshKey
    var showSourceSheet by rememberSaveable { mutableStateOf(false) }
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
            resolveHomeContent(lastWatchedChannelKey, flatChannels, favorites)
        }
    }
    val favoriteChannels = remember(homeContent.favorites) {
        homeContent.favorites.map { it.toChannel() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "notice") {
            LocalPremiumNotice.current(Modifier)
            app.zorik.player.ui.playlist.PlaylistPersistenceStatus(playlistState.sourceSaveState,
                source.onRetrySourceSave)
        }
        if (playlistState.hasChannels) {
            item(key = "source") {
                HomePlaylistCard(playlistState, epgState, totalChannels, favorites.size, onRefreshPlaylist,
                    onChangeSource = { showSourceSheet = true }, onOpenEpgSettings = source.onOpenEpgSettings,
                    modifier = Modifier.guidedTourTarget(GuidedTourKeys.PLAYLIST_ADD), onRetryEpg = source.onRetryEpg)
            }
            homeContent.continueWatching?.let { channel ->
                item(key = "continue") {
                    ContinueWatchingCard(channel, epgState, iconRefreshKey, resolveIcon,
                        onClick = {
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        })
                }
            }
            item(key = "shortcuts") { HomeQuickActions(onOpenChannels) }
            if (favoriteChannels.isNotEmpty()) {
                item(key = "favorites") {
                    HomeFavoritesRow(favoriteChannels, iconRefreshKey, resolveIcon,
                        onClick = { onChannelSelected(favoriteChannels, it) }, onOpenAll = onOpenFavorites)
                }
            }
            item(key = "categories") {
                HomeCategoryShelf(playlistState.groups, hiddenGroupKeys, onOpenCategory)
            }
            if (homeContent.continueWatching == null && favoriteChannels.isEmpty()) {
                item(key = "personalize") { HomePersonalizationCard() }
            }
            item(key = "ad") { BannerAdSlot(AdPlacement.HOME_BANNER, Modifier.fillMaxWidth()) }
        } else {
            if (playlistSources.isNotEmpty()) {
                item(key = "sources") {
                    PrimaryButton(stringResource(R.string.home_playlist_sources_title), { showSourceSheet = true },
                        leadingIcon = AppIcons.Channels, modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "empty") {
                if (playlistState.isLoading) HomeDashboardSkeleton()
                else HomeNoChannelsState(playlistState, onRefreshPlaylist, onOpenAddPlaylist)
            }
            item(key = "local") { app.zorik.player.ui.media.OpenLocalVideoButton() }
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
            saveState = playlistState.sourceSaveState,
            onRetrySave = source.onRetrySourceSave,
        )
    }
}

/** Fills the new-user Home state with a useful next step instead of leaving a tall blank canvas. */
@Composable
private fun HomePersonalizationCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.colors.material.primaryContainer,
                edgeColor = UaTheme.palette.hairline,
                shadow = false,
            )
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppIcons.Favorites,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.home_make_it_yours_title),
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(start = GapM),
            )
        }
        Text(
            text = stringResource(R.string.home_make_it_yours_body),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = GapS),
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
    val errorMessage = error?.asUserMessage()
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
        val retryExisting = errorMessage != null && playlistState.canRetryLoading
        PrimaryButton(
            text = stringResource(
                if (retryExisting) R.string.common_retry else R.string.home_add_playlist_button,
            ),
            onClick = if (retryExisting) onRefreshPlaylist else onOpenAddPlaylist,
            leadingIcon = if (retryExisting) AppIcons.Refresh else AppIcons.Upload,
            modifier = Modifier
                .padding(top = GapM)
                .fillMaxWidth()
                .guidedTourTarget(GuidedTourKeys.PLAYLIST_ADD),
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
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = false,
            )
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_continue_watching_label),
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_continue_watching_label),
            style = SectionLabel,
            color = UaTheme.palette.labelSecondary,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = GapM), verticalAlignment = Alignment.CenterVertically) {
            val largeFont = LocalDensity.current.fontScale >= 1.5f
            if (!largeFont) ChannelIcon(channel, resolveIcon, refreshKey = iconRefreshKey)
            Column(modifier = Modifier.weight(1f).padding(start = if (largeFont) 0.dp else 12.dp)) {
                Text(
                    text = channel.displayName,
                    style = BodyText.copy(fontFamily = UaTheme.palette.displayFontFamily),
                    color = UaTheme.palette.labelPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val programme = remember(channel, epgState.data, epgState.nowMillis) {
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
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    TrackProgress(
                        progress = ProgrammeProgress.progress(current.startMillis, effectiveStop, epgState.nowMillis),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            GradientPlayButton(AppIcons.Play, onClick, modifier = Modifier.padding(start = 8.dp),
                contentDescription = stringResource(R.string.player_play))
        }
    }
}

/** Up to [app.zorik.player.home.HomeContentPolicy.MAX_FAVORITES_SHOWN] favorites as a horizontal
 * shelf - hidden entirely when there are none, rather than showing an empty-state placeholder for
 * a section the user hasn't used yet. */
@Composable
private fun HomeFavoritesRow(
    channels: List<M3uChannel>,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onClick: (index: Int) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.TextButton(onClick = onOpenAll) {
            Text(stringResource(R.string.favorites_title), style = SectionLabel)
            Icon(AppIcons.ChevronDown, null, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
            horizontalArrangement = Arrangement.spacedBy(GapM),
        ) {
            itemsIndexed(channels, key = { index,
                channel -> app.zorik.player.playlist.ChannelListKeys.keyFor(index, channel.streamUrl) },
                contentType = { _, _ -> "favorite" }) { index, channel ->
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
        modifier = Modifier
            .width(132.dp)
            .panelSurface(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = channel.displayName, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelIcon(channel, resolveIcon, size = NpLogoSize, refreshKey = iconRefreshKey)
        Text(
            text = channel.displayName,
            style = Caption,
            color = UaTheme.palette.labelPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GapS),
        )
    }
}
