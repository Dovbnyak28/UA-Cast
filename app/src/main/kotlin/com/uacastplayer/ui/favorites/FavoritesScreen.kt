package com.uacastplayer.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.FavoritesSortOrder
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.favorites.FavoritesSorter
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.theme.Azure
import com.uacastplayer.ui.theme.LabelPrimary
import com.uacastplayer.ui.theme.LabelSecondary
import com.uacastplayer.ui.theme.AppIcons

@Composable
fun FavoritesScreen(
    favorites: List<FavoriteChannel>,
    playlistChannels: List<M3uChannel>,
    sortOrder: FavoritesSortOrder,
    onSortOrderSelected: (FavoritesSortOrder) -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onRemove: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        EmptyState(
            icon = AppIcons.Favorites,
            title = stringResource(R.string.favorites_empty_message),
            subtitle = stringResource(R.string.favorites_empty_subtitle),
            modifier = modifier,
        )
        return
    }

    val playlistIndexByKey = remember(playlistChannels) {
        playlistChannels.withIndex().associate { (index, channel) -> FavoriteKey.of(channel) to index }
    }
    val sortedFavorites = remember(favorites, sortOrder, playlistIndexByKey) {
        FavoritesSorter.sort(favorites, sortOrder) { playlistIndexByKey[it.key] }
    }
    val channels = remember(sortedFavorites) {
        sortedFavorites.map { fav ->
            M3uChannel(
                displayName = fav.displayName,
                streamUrl = fav.streamUrl,
                tvgId = fav.tvgId,
                groupTitle = fav.groupTitle,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f))
            FavoritesSortMenu(selected = sortOrder, onSelect = onSortOrderSelected)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(sortedFavorites, key = { _, favorite -> favorite.key }) { index, favorite ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelSelected(channels, index) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = favorite.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(favorite.key) }) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = stringResource(R.string.favorites_remove_content_description),
                            tint = MaterialTheme.colorScheme.error,
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
                tint = Azure,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FavoritesSortOrder.entries.forEach { order ->
                val isSelected = order == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(order.labelRes()),
                            color = if (isSelected) Azure else LabelPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            AppIcons.Sort,
                            contentDescription = null,
                            tint = if (isSelected) Azure else LabelSecondary,
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
}
