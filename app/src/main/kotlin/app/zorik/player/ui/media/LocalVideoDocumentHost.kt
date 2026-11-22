package app.zorik.player.ui.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zorik.player.R
import app.zorik.player.media.LocalVideo
import app.zorik.player.media.LocalVideoOpenViewModel
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.platform.launchDocumentPickerIfSupported
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.tv.TvDialogInputRegistration
import android.widget.Toast

internal val LocalOpenVideo = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun LocalVideoDocumentHost(
    onVideo: (LocalVideo) -> Unit, owner: LocalVideoOpenViewModel = viewModel(), content: @Composable () -> Unit,
) {
    val selection by owner.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { owner.open(it.toString()) }
    }
    LaunchedEffect(selection.requestId, selection.video) {
        val completed = selection
        completed.video?.let { video -> onVideo(video); owner.consumed(completed.requestId) }
    }
    CompositionLocalProvider(LocalOpenVideo provides {
        if (!picker.launchDocumentPickerIfSupported(arrayOf("*/*"), "open a video", context)) {
            Toast.makeText(context, R.string.local_video_picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }, content = content)
    if (selection.loading) {
        AlertDialog(
            onDismissRequest = owner::cancelSelection,
            title = { Text(stringResource(R.string.local_video_open)) },
            text = { TvDialogInputRegistration(); Text(stringResource(R.string.local_video_opening)) },
            confirmButton = {
                TextButton(onClick = owner::cancelSelection) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (selection.error) {
        AlertDialog(
            onDismissRequest = owner::dismissError,
            title = { Text(stringResource(R.string.local_video_open)) },
            text = { TvDialogInputRegistration(); Text(stringResource(R.string.local_video_open_failed)) },
            confirmButton = {
                TextButton(onClick = owner::dismissError) { Text(stringResource(R.string.common_close)) }
            },
        )
    }
}

/** No action in isolated surfaces without a document host; main composition owns the launcher. */
@Composable
fun OpenLocalVideoButton() {
    val open = LocalOpenVideo.current ?: return
    SecondaryButton(
        text = stringResource(R.string.local_video_open),
        onClick = open,
        leadingIcon = AppIcons.Play,
        modifier = Modifier.fillMaxWidth().padding(top = GapM),
    )
    Text(stringResource(R.string.local_video_formats_hint), style = Caption, color = UaTheme.palette.labelSecondary)
}
