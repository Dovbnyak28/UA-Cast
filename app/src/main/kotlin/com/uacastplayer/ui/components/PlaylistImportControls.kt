package com.uacastplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R

/** The URL-or-file playlist import row shared by the Home and Channels screens. */
@Composable
fun PlaylistImportControls(
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlInput by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.playlist_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onLoadUrl(urlInput) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_load_button))
            }
            OutlinedButton(onClick = onPickFile) {
                Text(stringResource(R.string.playlist_browse_file))
            }
        }
    }
}
