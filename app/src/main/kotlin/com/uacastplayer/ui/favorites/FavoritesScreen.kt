package com.uacastplayer.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.theme.AppIcons

@Composable
fun FavoritesScreen(
    favorites: List<FavoriteChannel>,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onRemove: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.favorites_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val channels = favorites.map { fav ->
        M3uChannel(
            displayName = fav.displayName,
            streamUrl = fav.streamUrl,
            tvgId = fav.tvgId,
            groupTitle = fav.groupTitle,
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        itemsIndexed(favorites, key = { _, favorite -> favorite.key }) { index, favorite ->
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
                    Icon(AppIcons.Favorites, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
