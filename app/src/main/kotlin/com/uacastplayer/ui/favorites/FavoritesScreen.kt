package com.uacastplayer.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.uacastplayer.R
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesPlaylistPositions
import com.uacastplayer.data.prefs.currentAppLanguage
import com.uacastplayer.favorites.FavoritesSorter
import com.uacastplayer.favorites.ReorderPolicy
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.AppIcons
import java.io.File

@Composable
fun FavoritesScreen(
    favorites: List<FavoriteChannel>,
    playlistChannels: List<M3uChannel>,
    sortOrder: FavoritesSortOrder,
    onSortOrderSelected: (FavoritesSortOrder) -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onRemove: (key: String) -> Unit,
    onReorder: (List<FavoriteChannel>) -> Unit,
    onOpenChannels: () -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        EmptyState(
            icon = AppIcons.Favorites,
            title = stringResource(R.string.favorites_empty_message),
            subtitle = stringResource(R.string.favorites_empty_subtitle),
            primaryActionLabel = stringResource(R.string.favorites_go_to_channels),
            onPrimaryAction = onOpenChannels,
            modifier = modifier,
        )
        return
    }

    val favoriteKeys = remember(favorites) { favorites.mapTo(LinkedHashSet(favorites.size)) { it.key } }
    val playlistIndexByKey by produceState<Map<String, Int>>(
        initialValue = emptyMap(),
        playlistChannels,
        favoriteKeys,
        sortOrder,
    ) {
        value = if (sortOrder == FavoritesSortOrder.PLAYLIST_ORDER) {
            withPlaylistCpu { FavoritesPlaylistPositions.resolve(playlistChannels, favoriteKeys) }
        } else {
            emptyMap()
        }
    }
    // The language chosen in this app, not the device's - see AppLanguage.toLocale for why those
    // differ here. Keyed into the remember so switching language re-sorts rather than keeping the
    // previous alphabet until something else invalidates this.
    val sortLocale = LocalContext.current.currentAppLanguage().toLocale()
    val sortedFavorites = remember(favorites, sortOrder, playlistIndexByKey, sortLocale) {
        FavoritesSorter.sort(favorites, sortOrder, sortLocale) { playlistIndexByKey[it.key] }
    }
    // A local snapshot list the drag gesture mutates live for immediate visual feedback; only
    // re-seeded when the upstream order actually changes (not on every recomposition), so an
    // in-progress drag isn't reset out from under the user - see onReorder below.
    val reorderState = rememberFavoriteReorderState(sortedFavorites)
    val orderedFavorites = reorderState.ordered
    var editing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleFavorites by remember(orderedFavorites, query) {
        derivedStateOf { orderedFavorites.filter { it.displayName.contains(query.trim(), ignoreCase = true) } }
    }
    val channels = remember(visibleFavorites) { visibleFavorites.map { it.toChannel() } }
    val canReorder = editing && query.isBlank() && sortOrder == FavoritesSortOrder.MANUAL

    Column(modifier = modifier.fillMaxSize().padding(horizontal = ScreenHPadding, vertical = GapM)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { editing = !editing }, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (editing) R.string.common_close else R.string.favorites_edit))
            }
            FavoritesSortMenu(selected = sortOrder, onSelect = onSortOrderSelected)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.favorites_search)) },
            singleLine = true,
            colors = uaTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (visibleFavorites.isEmpty()) Text(stringResource(R.string.player_channels_no_results))
        if (canReorder) {
            Text(
                text = stringResource(R.string.favorites_manual_hint),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(visibleFavorites, key = { _, favorite -> favorite.key }) { index, favorite ->
                val isDragging = reorderState.draggedIndex == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Only the non-dragged rows get the automatic placement animation - the
                        // dragged row's position is already fully driven by the graphicsLayer
                        // translation below every frame, so animating it too would fight that and
                        // produce a stutter. This is what makes rows smoothly slide out of the way
                        // as you drag one past them, and makes removing a favorite collapse the
                        // list instead of the rows below it just jumping up.
                        .then(if (isDragging) Modifier else Modifier.animateItem())
                        .favoriteDrag(reorderState, favorite.key, index, canReorder, onReorder)
                        .clickable(enabled = !editing) { onChannelSelected(channels, index) }
                        .padding(ItemPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelIcon(channels[index], resolveIcon)
                    Text(
                        text = favorite.displayName,
                        style = BodyText,
                        color = UaTheme.palette.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    if (editing) {
                        FavoriteEditActions(
                            name = favorite.displayName,
                            onRemove = { onRemove(favorite.key) },
                            onMoveUp = if (canReorder && index > 0) {
                                { onReorder(ReorderPolicy.move(orderedFavorites.toList(), index, index - 1)) }
                            } else null,
                            onMoveDown = if (canReorder && index < orderedFavorites.lastIndex) {
                                { onReorder(ReorderPolicy.move(orderedFavorites.toList(), index, index + 1)) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

/** Toolbar control that switches the Favorites screen's [FavoritesSortOrder]. */
@Composable
private fun FavoritesSortMenu(selected: FavoritesSortOrder, onSelect: (FavoritesSortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = AppIcons.Sort,
                contentDescription = stringResource(R.string.favorites_sort_title),
                tint = UaTheme.palette.azure,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FavoritesSortOrder.entries.forEach { order ->
                val isSelected = order == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(order.labelRes()),
                            color = if (isSelected) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            AppIcons.Sort,
                            contentDescription = null,
                            tint = if (isSelected) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(order)
                    },
                )
            }
        }
    }
}

private fun FavoritesSortOrder.labelRes(): Int = when (this) {
    FavoritesSortOrder.PLAYLIST_ORDER -> R.string.favorites_sort_playlist_order
    FavoritesSortOrder.ALPHABETICAL -> R.string.favorites_sort_alphabetical
    FavoritesSortOrder.RECENTLY_ADDED -> R.string.favorites_sort_recently_added
    FavoritesSortOrder.MANUAL -> R.string.favorites_sort_manual
}
