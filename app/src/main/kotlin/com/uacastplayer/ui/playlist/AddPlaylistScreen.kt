package com.uacastplayer.ui.playlist
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.playlist.CleartextCredentialPolicy
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Feature
import com.uacastplayer.playlist.XtreamUrlBuilder
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.appBackground
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.Title

/** Which kind of source [AddPlaylistScreen] is currently configured for - just UI state, not
 * persisted anywhere: once a source loads, all three converge on the same URL/file pipeline. */
private enum class PlaylistSourceType { URL, FILE, XTREAM }

/** Dedicated full-screen "add playlist" flow, reached from Home/Channels/Settings. */
@Composable
fun AddPlaylistScreen(
    playlistState: PlaylistUiState,
    onSetDisplayName: (String) -> Unit,
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit,
    onLoadXtream: (server: String, username: String, password: String) -> Unit,
    onBackClick: () -> Unit,
    onPlaylistLoaded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceType by rememberSaveable { mutableStateOf(PlaylistSourceType.URL) }
    var name by rememberSaveable { mutableStateOf(playlistState.displayName.orEmpty()) }
    var url by rememberSaveable { mutableStateOf("") }
    val gate = LocalFeatureGate.current
    var xtreamServer by rememberSaveable { mutableStateOf("") }
    var xtreamUsername by rememberSaveable { mutableStateOf("") }
    var xtreamPassword by rememberSaveable { mutableStateOf("") }
    var xtreamPasswordVisible by rememberSaveable { mutableStateOf(false) }
    // Only auto-dismiss once we've actually observed a load-in-progress from this screen -
    // otherwise this fires immediately whenever a playlist was already loaded before opening it.
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(playlistState.isLoading) {
        if (playlistState.isLoading) hasStartedLoading = true
    }
    LaunchedEffect(hasStartedLoading, playlistState.hasChannels, playlistState.isLoading, playlistState.error) {
        // Guard against a *replacement* load failing: the old playlist's hasChannels/groups stay
        // true (applyPlaylistOutcome preserves them on error), so without the error check this
        // would auto-dismiss and hide the failure instead of leaving the status card showing it.
        val loadSucceeded = listOf(
            hasStartedLoading,
            playlistState.hasChannels,
            !playlistState.isLoading,
            playlistState.error == null,
        ).all { it }
        if (loadSucceeded) {
            // Only persist the name once the load has actually succeeded - setting it eagerly on
            // tap would silently overwrite the existing name even when the load no-ops (blank URL)
            // or the user cancels the file picker before a file is ever chosen.
            onSetDisplayName(effectiveDisplayName(name, sourceType, xtreamServer))
            onPlaylistLoaded()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .appBackground()
            // safeDrawing, not statusBars alone, and outside the scroll so it shrinks the viewport
            // rather than scrolling away: this is a full-bleed gate screen, so nothing above it pads
            // the bottom, and safeDrawing also covers the IME - which matters here more than on the
            // other gates, since this is the one screen with text fields to type into.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenHPadding),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = GapM)) {
            IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = UaTheme.palette.labelPrimary,
                )
            }
            Text(
                text = stringResource(R.string.add_playlist_title),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.add_playlist_name_hint)) },
            singleLine = true,
            colors = uaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = GapL),
        )

        SegmentedControl(
            options = listOf(
                stringResource(R.string.add_playlist_type_url),
                stringResource(R.string.add_playlist_type_file),
                stringResource(R.string.add_playlist_type_xtream),
            ),
            selectedIndex = sourceType.ordinal,
            onSelected = { index ->
                val chosen = PlaylistSourceType.entries[index]
                // Refused here rather than at the load button: a paywall that waits until after the
                // server, username and password have been typed in has taken the user's time first
                // and told them the price second.
                if (chosen == PlaylistSourceType.XTREAM) {
                    gate.guard(Feature.XTREAM) { sourceType = chosen }()
                } else {
                    sourceType = chosen
                }
            },
            modifier = Modifier.padding(top = GapM),
        )

        when (sourceType) {
            PlaylistSourceType.URL -> UrlSourceFields(url = url, onUrlChange = { url = it })
            PlaylistSourceType.FILE -> Unit // The file-picker action below is this mode's only input.
            PlaylistSourceType.XTREAM -> XtreamSourceFields(
                server = xtreamServer,
                username = xtreamUsername,
                password = xtreamPassword,
                passwordVisible = xtreamPasswordVisible,
                onServerChange = { xtreamServer = it },
                onUsernameChange = { xtreamUsername = it },
                onPasswordChange = { xtreamPassword = it },
                onPasswordVisibilityChanged = { xtreamPasswordVisible = it },
            )
        }

        if (hasLoadFeedback(playlistState)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GapL)
                    .raisedSurface(
                        RoundedCornerShape(RadiusCard),
                        UaTheme.palette.surface1,
                        edgeColor = UaTheme.palette.hairline,
                        shadow = false,
                    )
                    .padding(horizontal = CardPadding, vertical = GapM),
            ) {
                Text(
                    text = stringResource(R.string.add_playlist_status_label),
                    style = SectionLabel,
                    color = UaTheme.palette.labelSecondary,
                )
                Text(
                    text = loadFeedbackMessage(playlistState),
                    style = BodyText,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SourceActionButton(
            sourceType = sourceType,
            url = url,
            xtreamServer = xtreamServer,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            isLoading = playlistState.isLoading,
            onLoadUrl = onLoadUrl,
            onLoadXtream = onLoadXtream,
            onPickFile = onPickFile,
        )

        Text(
            text = stringResource(R.string.add_playlist_tip),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = GapL, bottom = GapL),
        )
    }
}

/** An Xtream source with no custom name defaults to its server host instead of falling through to
 * the fingerprint-based label the other two source types get from a blank name. */
private fun effectiveDisplayName(name: String, sourceType: PlaylistSourceType, xtreamServer: String): String =
    if (name.isBlank() && sourceType == PlaylistSourceType.XTREAM) XtreamUrlBuilder.serverHost(xtreamServer) else name

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
private fun SourceActionButton(
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
private fun UrlSourceFields(url: String, onUrlChange: (String) -> Unit) {
    val invalidUrl = url.isNotBlank() && !PlaylistSourceInputValidator.isValidHttpUrl(url)
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
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
                IconButton(onClick = { onUrlChange("") }) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(R.string.cache_clear_button),
                        tint = UaTheme.palette.labelSecondary,
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = uaTextFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = GapM),
    )
    Text(
        text = stringResource(R.string.add_playlist_helper),
        style = Caption,
        color = UaTheme.palette.labelSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun XtreamSourceFields(
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
        modifier = Modifier.fillMaxWidth().padding(top = GapM),
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
        modifier = Modifier.fillMaxWidth().padding(top = GapM),
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
        modifier = Modifier.fillMaxWidth().padding(top = GapM),
    )
}

private fun hasLoadFeedback(playlistState: PlaylistUiState): Boolean =
    playlistState.isLoading ||
        playlistState.error != null ||
        (playlistState.activePlaylistId != null && !playlistState.hasChannels)

@Composable
private fun loadFeedbackMessage(playlistState: PlaylistUiState): String = when {
    playlistState.isLoading -> stringResource(R.string.add_playlist_status_loading)
    playlistState.error != null -> when (val error = playlistState.error) {
        PlaylistError.SizeLimitExceeded -> stringResource(R.string.playlist_error_size_limit)
        is PlaylistError.Http -> stringResource(R.string.playlist_error_http, error.code)
        PlaylistError.Network -> stringResource(R.string.playlist_error_network)
        PlaylistError.Empty -> stringResource(R.string.playlist_error_empty)
    }
    // A load that finished, reported no error, and produced nothing. Without this branch the
    // status fell through to "ready to load" - the exact words shown *before* the button was pressed,
    // so a user whose provider returned 200 with an empty body, an HTML error page, or bytes that
    // are not a playlist at all had no way to tell a failure from a tap that never registered.
    // Reached whenever the response parses to zero channels; `activePlaylistId` is what says a load
    // actually ran, since PlaylistOutcomeReducer sets it on every Loaded outcome including this one.
    playlistState.activePlaylistId != null && !playlistState.hasChannels ->
        stringResource(R.string.add_playlist_status_no_channels)
    else -> ""
}
