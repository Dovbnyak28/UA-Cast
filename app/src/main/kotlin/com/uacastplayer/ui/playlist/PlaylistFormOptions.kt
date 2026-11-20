package com.uacastplayer.ui.playlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.tvTextFieldNavigation

@Composable
internal fun AddPlaylistProviderHint(hasSources: Boolean) {
    if (hasSources) {
        Text(stringResource(R.string.add_playlist_returning_hint), style = Caption,
            color = UaTheme.palette.labelSecondary, modifier = Modifier.padding(top = GapM))
    } else ProviderSourceHint()
}

@Composable
internal fun PlaylistNameOptions(name: String, onNameChanged: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = GapM)) {
        Text(stringResource(if (expanded) R.string.add_playlist_less else R.string.add_playlist_advanced))
    }
    if (expanded) OutlinedTextField(
        value = name, onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.add_playlist_name_hint)) }, singleLine = true,
        colors = uaTextFieldColors(), modifier = Modifier.fillMaxWidth().tvTextFieldNavigation(),
    )
}
