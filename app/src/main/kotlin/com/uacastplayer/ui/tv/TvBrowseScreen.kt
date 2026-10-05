package com.uacastplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.data.playlist.withPlaylistCpuCancellable
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.favorites.FavoritesSorter
import com.uacastplayer.favorites.FavoritesPlaylistPositions
import com.uacastplayer.data.prefs.currentAppLanguage
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.ChannelListFilter
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.home.PlaylistSourceSheet
import com.uacastplayer.ui.playlist.asUserMessage
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import java.io.File
import java.util.Locale

/** TV browsing keeps the shared playlist, parental gate and PlayerHost; only presentation changes. */
@Composable
fun TvBrowseScreen(
    destination: BottomDestination,
    playlist: PlaylistUiState,
    source: HomeSourceState,
    favorites: List<FavoriteChannel>,
    hiddenGroupKeys: Set<String>,
    resolveIcon: suspend (M3uChannel) -> File?,
    onChannelSelected: (List<M3uChannel>, Int) -> Unit,
    modifier: Modifier = Modifier,
    iconRefreshKey: Any? = null,
    favoriteSortOrder: FavoritesSortOrder = FavoritesSortOrder.DEFAULT,
) {
    var query by rememberSaveable(destination) { mutableStateOf("") }
    val initialFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val clearSearch: () -> Unit = { query = ""; searchFocus.requestFocus() }
    var initialFocusPending by remember(destination) { mutableStateOf(true) }
    val locale = LocalContext.current.currentAppLanguage().toLocale()
    val favoriteChannels by produceState<List<M3uChannel>>(emptyList(), playlist.channels, favorites,
        favoriteSortOrder, locale, destination) {
        value = if (destination == BottomDestination.FAVORITES) withPlaylistCpu {
            sortedTvFavorites(favorites, playlist.channels, favoriteSortOrder, locale)
        } else emptyList()
    }
    val visibleChannels by produceState<List<M3uChannel>>(emptyList(), playlist.channels, playlist.groups,
        favoriteChannels, query,
        hiddenGroupKeys, destination) {
        value = withPlaylistCpuCancellable { checkCancellation ->
            val favoritesOnly = destination == BottomDestination.FAVORITES
            ChannelListFilter.filter(
                if (favoritesOnly) favoriteChannels else playlist.channels,
                playlist.groups,
                if (favoritesOnly) emptySet() else hiddenGroupKeys,
                query,
                checkCancellation,
            )
        }
    }
    // Capture the rendered snapshot so an in-flight search cannot change a visible card's index.
    val displayedChannels = visibleChannels
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tv_dpad_hint), style = Caption, color = UaTheme.palette.labelSecondary)
        if (destination == BottomDestination.HOME) TvSourceActions(source)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.channels_search_hint)) }, colors = uaTextFieldColors(),
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = clearSearch,
                    modifier = Modifier.tvFocus().testTag("tv_search_clear")) {
                    Icon(AppIcons.Close, stringResource(R.string.channels_clear_search))
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                .tvTextFieldNavigation().testTag("tv_search"),
        )
        playlist.error?.let { Text(it.asUserMessage(), color = UaTheme.palette.labelPrimary) }
        if (playlist.isLoading) CircularProgressIndicator()
        if (visibleChannels.isEmpty() && !playlist.isLoading) {
            TvBrowseEmptyState(destination, favorites.isEmpty(), playlist.hasChannels, query,
                source.onOpenAddPlaylist, clearSearch)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Indices also distinguish duplicate URLs/tvg-ids in imperfect provider playlists.
            itemsIndexed(displayedChannels, key = { index, _ -> index }) { index, channel ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).testTag("tv_channel_$index")
                        .then(if (index == 0) Modifier.focusRequester(initialFocus).onGloballyPositioned {
                            if (initialFocusPending) {
                                initialFocusPending = false
                                if (query.isBlank()) initialFocus.requestFocus()
                            }
                        } else Modifier)
                        .background(UaTheme.palette.surface1, RoundedCornerShape(12.dp)).tvFocus()
                        .clickable(role = Role.Button) { onChannelSelected(displayedChannels, index) }.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    ChannelIcon(channel, resolveIcon, size = 48.dp, refreshKey = iconRefreshKey)
                    Text(channel.displayName, style = BodyText, color = UaTheme.palette.labelPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TvBrowseEmptyState(destination: BottomDestination, favoritesEmpty: Boolean,
    hasChannels: Boolean, query: String, addPlaylist: () -> Unit, clearSearch: () -> Unit) {
    val emptyFavorites = destination == BottomDestination.FAVORITES && favoritesEmpty
    val hasContent = hasChannels || (destination == BottomDestination.FAVORITES && !favoritesEmpty) ||
        query.isNotBlank()
    val title = when {
        emptyFavorites -> R.string.favorites_empty_message
        hasContent -> R.string.player_channels_no_results
        else -> R.string.home_empty_message
    }
    Column(Modifier.fillMaxWidth().background(UaTheme.palette.surface1, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            val icon = if (emptyFavorites) AppIcons.Favorites
                else if (hasContent) AppIcons.Search else AppIcons.Channels
            Icon(icon, null, tint = UaTheme.palette.azure, modifier = Modifier.size(32.dp))
            Text(stringResource(title), style = Title, color = UaTheme.palette.labelPrimary,
                modifier = Modifier.weight(1f))
        }
        when {
            emptyFavorites -> Text(stringResource(R.string.favorites_empty_subtitle), style = BodyText,
                color = UaTheme.palette.labelSecondary)
            !hasContent -> {
                Text(stringResource(R.string.home_empty_subtitle), style = BodyText,
                    color = UaTheme.palette.labelSecondary)
                PrimaryButton(stringResource(R.string.add_playlist_title), addPlaylist)
            }
        }
        // A trailing icon inside the editable field is not reachable with Down when there is no grid.
        if (query.isNotEmpty()) SecondaryButton(stringResource(R.string.channels_clear_search), clearSearch,
            Modifier.testTag("tv_search_clear_empty"), leadingIcon = AppIcons.Close)
    }
}

private fun sortedTvFavorites(favorites: List<FavoriteChannel>, channels: List<M3uChannel>,
    sortOrder: FavoritesSortOrder, locale: Locale): List<M3uChannel> {
    val positions = if (sortOrder == FavoritesSortOrder.PLAYLIST_ORDER) {
        FavoritesPlaylistPositions.resolve(channels, favorites.mapTo(HashSet()) { it.key })
    } else emptyMap()
    return FavoritesSorter.sort(favorites, sortOrder, locale) { positions[it.key] }.map { it.toChannel() }
}

@Composable
private fun TvSourceActions(source: HomeSourceState) {
    var manage by remember { mutableStateOf(false) }
    if (manage) PlaylistSourceSheet(source.playlistSources, source.activePlaylistSourceId,
        onSelect = { source.onSwitchPlaylistSource(it); manage = false }, onRemove = source.onRemovePlaylistSource,
        onAddNew = { manage = false; source.onOpenAddPlaylist() }, onDismiss = { manage = false },
        onRetrySave = source.onRetrySourceSave)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(stringResource(R.string.add_playlist_title), source.onOpenAddPlaylist)
        SecondaryButton(stringResource(R.string.common_retry), source.onRefreshPlaylist)
        SecondaryButton(stringResource(R.string.home_playlist_sources_title), { manage = true })
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(source.playlistSources, key = { it.id }) { playlist ->
            SecondaryButton(playlist.displayName ?: stringResource(R.string.app_name),
                { source.onSwitchPlaylistSource(playlist) })
        }
    }
}
