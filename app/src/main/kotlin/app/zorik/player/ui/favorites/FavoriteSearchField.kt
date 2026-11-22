package app.zorik.player.ui.favorites

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.zorik.player.R
import app.zorik.player.ui.components.uaTextFieldColors
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.UaTheme

@Composable
internal fun FavoriteSearchField(query: String, onQueryChanged: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChanged,
        label = { Text(stringResource(R.string.favorites_search)) },
        leadingIcon = { Icon(AppIcons.Search, null, tint = UaTheme.palette.labelSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChanged("") }) {
                Icon(AppIcons.Close, stringResource(R.string.channels_clear_search),
                    tint = UaTheme.palette.labelSecondary)
            }
        },
        singleLine = true, colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("favorites_search"),
    )
}

internal fun filteredFavorites(items: List<app.zorik.player.favorites.FavoriteChannel>, query: String) =
    if (query.isEmpty()) items else items.filter { it.displayName.contains(query, ignoreCase = true) }
