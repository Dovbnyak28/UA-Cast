package com.uacastplayer.ui.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.playlist.PlaylistError

/** One localized wording for the same playlist failure across Home, Channels and the add flow. */
@Composable
internal fun PlaylistError.asUserMessage(): String = when (this) {
    PlaylistError.SizeLimitExceeded -> stringResource(R.string.playlist_error_size_limit)
    PlaylistError.ChannelLimitExceeded -> stringResource(R.string.playlist_error_channel_limit)
    is PlaylistError.Http -> stringResource(R.string.playlist_error_http, code)
    PlaylistError.Network -> stringResource(R.string.playlist_error_network)
    PlaylistError.Storage -> stringResource(R.string.playlist_error_storage)
    PlaylistError.Empty -> stringResource(R.string.playlist_error_empty)
}
