package com.uacastplayer.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.uacastplayer.R
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.UaTheme

/** Render the owner's durability outcome; no file operations or local copy of save state. */
@Composable
internal fun PlaylistPersistenceStatus(
    state: PlaylistSourceSaveState,
    onRetry: () -> Unit,
    retryEnabled: Boolean = true,
) {
    val message = when (state) {
        PlaylistSourceSaveState.SAVING -> stringResource(R.string.playlist_sources_saving)
        PlaylistSourceSaveState.FAILED -> stringResource(R.string.playlist_sources_save_failed)
        PlaylistSourceSaveState.LIMIT_REACHED ->
            stringResource(R.string.playlist_sources_limit, PlaylistSourcePolicy.MAX_SOURCES)
        else -> return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = GapM).testTag("playlist-persistence-status")) {
        Text(message, style = BodyText, color = UaTheme.palette.labelPrimary)
        if (state == PlaylistSourceSaveState.FAILED) {
            SecondaryButton(
                stringResource(R.string.playlist_sources_retry_save), onRetry,
                Modifier.fillMaxWidth().padding(top = GapM), enabled = retryEnabled,
            )
        }
    }
}
