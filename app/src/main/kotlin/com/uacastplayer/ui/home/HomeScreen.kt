package com.uacastplayer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.PlaylistImportControls
import com.uacastplayer.ui.theme.AppIcons

@Composable
fun HomeScreen(
    playlistState: PlaylistUiState,
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlistState.hasChannels) {
        EmptyState(
            icon = AppIcons.Channels,
            title = stringResource(R.string.home_playlist_loaded_title),
            subtitle = stringResource(R.string.home_playlist_loaded_subtitle),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        IconHeader(
            icon = AppIcons.Upload,
            title = stringResource(R.string.home_empty_message),
            subtitle = stringResource(R.string.home_empty_subtitle),
            modifier = Modifier.fillMaxWidth(),
        )
        PlaylistImportControls(
            onLoadUrl = onLoadUrl,
            onPickFile = onPickFile,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
