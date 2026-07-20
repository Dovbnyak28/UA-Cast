package com.uacastplayer.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.favorites.FavoritesSorter
import com.uacastplayer.favorites.ReorderPolicy
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface
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
        Column(modifier = modifier.fillMaxSize().padding(horizontal = ScreenHPadding, vertical = GapM)) {
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
                    .padding(CardPadding),
            ) {
                Text(
                    text = stringResource(R.string.favorites_empty_message),
                    style = Title,
                    color = UaTheme.palette.labelPrimary,
                )
                Text(
                    text = stringResource(R.string.favorites_empty_subtitle),
                    style = BodyText,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = onOpenChannels, modifier = Modifier.fillMaxWidth().padding(top = GapL)) {
                    Text(stringResource(R.string.favorites_go_to_channels))
                }
            }
        }
        return
    }

    val playlistIndexByKey = remember(playlistChannels) {
        playlistChannels.withIndex().associate { (index, channel) -> FavoriteKey.of(channel) to index }
    }
    val sortedFavorites = remember(favorites, sortOrder, playlistIndexByKey) {
        FavoritesSorter.sort(favorites, sortOrder) { playlistIndexByKey[it.key] }
    }
    // A local snapshot list the drag gesture mutates live for immediate visual feedback; only
    // re-seeded when the upstream order actually changes (not on every recomposition), so an
    // in-progress drag isn't reset out from under the user - see onReorder below.
    val orderedFavorites = remember(sortedFavorites) { sortedFavorites.toMutableStateList() }
    val channels = orderedFavorites.map { fav ->
        M3uChannel(
            displayName = fav.displayName,
            streamUrl = fav.streamUrl,
            tvgId = fav.tvgId,
            groupTitle = fav.groupTitle,
        )
    }
    var rowHeightPx by remember { mutableStateOf(0f) }
    var dragState by remember { mutableStateOf<FavoriteDragState?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f))
            FavoritesSortMenu(selected = sortOrder, onSelect = onSortOrderSelected)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(orderedFavorites, key = { _, favorite -> favorite.key }) { index, favorite ->
                val isDragging = dragState?.draggedIndex == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = if (isDragging) dragState?.offsetY ?: 0f else 0f }
                        .onSizeChanged { rowHeightPx = it.height.toFloat() }
                        .clickable { onChannelSelected(channels, index) }
                        .then(
                            if (sortOrder == FavoritesSortOrder.MANUAL) {
                                Modifier.pointerInput(favorite.key) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragState = FavoriteDragState(index, 0f) },
                                        onDragEnd = {
                                            dragState = null
                                            onReorder(orderedFavorites.toList())
                                        },
                                        onDragCancel = { dragState = null },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val state = dragState ?: return@detectDragGesturesAfterLongPress
                                            val newOffsetY = state.offsetY + dragAmount.y
                                            val delta = ReorderPolicy.indexDelta(newOffsetY, rowHeightPx)
                                            val targetIndex = (state.draggedIndex + delta).coerceIn(orderedFavorites.indices)
                                            if (delta != 0 && targetIndex != state.draggedIndex) {
                                                val moved = ReorderPolicy.move(orderedFavorites.toList(), state.draggedIndex, targetIndex)
                                                orderedFavorites.clear()
                                                orderedFavorites.addAll(moved)
                                                val consumedOffset = (targetIndex - state.draggedIndex) * rowHeightPx
                                                dragState = FavoriteDragState(targetIndex, newOffsetY - consumedOffset)
                                            } else {
                                                dragState = state.copy(offsetY = newOffsetY)
                                            }
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(ItemPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelIcon(channels[index], resolveIcon)
                    Text(
                        text = favorite.displayName,
                        style = BodyText,
                        color = UaTheme.palette.labelPrimary,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onRemove(favorite.key) }) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = stringResource(R.string.favorites_remove_content_description),
                            tint = UaTheme.palette.routeRed,
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

/** [offsetY] is the dragged row's accumulated vertical translation (px) since the drag started -
 * reset relative to [draggedIndex] each time a swap happens, so it stays continuous. */
private data class FavoriteDragState(val draggedIndex: Int, val offsetY: Float)
