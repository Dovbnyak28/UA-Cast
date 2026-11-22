package app.zorik.player.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import app.zorik.player.R
import app.zorik.player.playlist.PlaylistSourcePolicy
import app.zorik.player.playlist.PlaylistSourceSaveState
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.UaTheme

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
