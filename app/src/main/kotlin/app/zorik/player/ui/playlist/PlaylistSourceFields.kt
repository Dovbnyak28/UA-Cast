package app.zorik.player.ui.playlist
import app.zorik.player.ui.theme.UaTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.zorik.player.ui.tv.tvTextFieldNavigation
import app.zorik.player.ui.tv.tvFocus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import app.zorik.player.R
import kotlinx.coroutines.launch
import app.zorik.player.playlist.CleartextCredentialPolicy
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.components.SecondaryButton
import app.zorik.player.ui.components.uaTextFieldColors
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.CaptionSemibold
import app.zorik.player.ui.theme.GapL
import app.zorik.player.ui.theme.GapM


/**
 * [isLoading] disables the button for the duration of a load, and that is not cosmetic. Every tap
 * used to start another independent load: six taps during one slow request produced six concurrent
 * downloads, six parses of up to `MAX_PLAYLIST_BYTES` each, and a final state decided by whichever
 * response happened to land last. A user has every reason to tap again - a hung server leaves the
 * status on "Loading…" for about a minute and a half while the loader retries, with nothing on
 * screen to say a request is still in flight.
 *
 * `RefreshPlaylistButton` on Home has always guarded its own load this way; this is the same guard
 * on the screen where the load actually starts.
 */
@Composable
internal fun SourceActionButton(
    sourceType: PlaylistSourceType,
    url: String,
    xtreamServer: String,
    xtreamUsername: String,
    xtreamPassword: String,
    isLoading: Boolean,
    onLoadUrl: (String) -> Unit,
    onLoadXtream: (server: String, username: String, password: String) -> Unit,
    onPickFile: () -> Unit,
) {
    if (sourceType == PlaylistSourceType.FILE) {
        SecondaryButton(
            text = stringResource(R.string.add_playlist_file_button),
            onClick = onPickFile,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = GapL),
            leadingIcon = AppIcons.Upload,
        )
    } else {
        val isInputValid = when (sourceType) {
            PlaylistSourceType.URL -> PlaylistSourceInputValidator.isValidHttpUrl(url)
            PlaylistSourceType.XTREAM -> PlaylistSourceInputValidator.isValidXtream(
                xtreamServer,
                xtreamUsername,
                xtreamPassword,
            )
            PlaylistSourceType.FILE -> true
        }
        PrimaryButton(
            text = stringResource(R.string.add_playlist_save_button),
            onClick = {
                if (sourceType == PlaylistSourceType.URL) {
                    onLoadUrl(url.trim())
                } else {
                    onLoadXtream(xtreamServer.trim(), xtreamUsername.trim(), xtreamPassword)
                }
            },
            enabled = isInputValid && !isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = GapL),
        )
    }
}

@Composable
internal fun UrlSourceFields(url: String, onUrlChange: (String) -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentUrl by androidx.compose.runtime.rememberUpdatedState(url)
    var editGeneration by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val onEdit = { value: String -> editGeneration++; onUrlChange(value) }
    TextButton(onClick = {
        val requestedFor = url
        val requestedGeneration = ++editGeneration
        scope.launch {
            val text = readClipboardLink(clipboard)
            if (text != null && editGeneration == requestedGeneration && currentUrl == requestedFor) {
                onEdit(text.trim())
            }
        }
    }, modifier = Modifier.padding(top = GapM).tvFocus()) {
        Text(stringResource(R.string.add_playlist_paste_link))
    }
    val invalidUrl = url.isNotBlank() && !PlaylistSourceInputValidator.isValidHttpUrl(url)
    OutlinedTextField(
        value = url,
        onValueChange = onEdit,
        label = { Text(stringResource(R.string.add_playlist_url_hint)) },
        singleLine = true,
        isError = invalidUrl,
        supportingText = if (invalidUrl) {
            { Text(stringResource(R.string.add_playlist_url_error)) }
        } else {
            null
        },
        trailingIcon = if (url.isNotBlank()) {
            {
                IconButton(onClick = { onEdit("") }) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(R.string.add_playlist_clear_url),
                        tint = UaTheme.palette.labelSecondary,
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().tvTextFieldNavigation().padding(top = GapM),
    )
    Text(
        text = stringResource(R.string.add_playlist_helper),
        style = Caption,
        color = UaTheme.palette.labelSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (CleartextCredentialPolicy.isCleartextPlaylistUrl(url)) {
        Text(
            text = stringResource(R.string.add_playlist_url_cleartext_warning),
            style = Caption,
            color = UaTheme.palette.routeRed,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun XtreamSourceFields(
    server: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChanged: (Boolean) -> Unit,
) {
    val invalidServer = server.isNotBlank() && !PlaylistSourceInputValidator.isValidXtreamServer(server)
    OutlinedTextField(
        value = server,
        onValueChange = onServerChange,
        label = { Text(stringResource(R.string.add_playlist_xtream_server_hint)) },
        singleLine = true,
        isError = invalidServer,
        supportingText = if (invalidServer) {
            { Text(stringResource(R.string.add_playlist_server_error)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().tvTextFieldNavigation().padding(top = GapM),
    )
    if (CleartextCredentialPolicy.exposesCredentials(server)) {
        Text(
            text = stringResource(R.string.add_playlist_xtream_cleartext_warning),
            style = Caption,
            color = UaTheme.palette.routeRed,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.add_playlist_xtream_username_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().tvTextFieldNavigation().padding(top = GapM),
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.add_playlist_xtream_password_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { onPasswordVisibilityChanged(!passwordVisible) }) {
                Text(
                    text = stringResource(
                        if (passwordVisible) R.string.add_playlist_password_hide
                        else R.string.add_playlist_password_show,
                    ),
                    style = CaptionSemibold,
                    color = UaTheme.palette.accentText,
                )
            }
        },
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().tvTextFieldNavigation().padding(top = GapM),
    )
}

/** Text only: never dereference a clipboard URI or load its provider/network content. */
private suspend fun readClipboardLink(clipboard: androidx.compose.ui.platform.Clipboard): String? {
    val clip = clipboard.getClipEntry()?.clipData ?: return null
    return if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
}
