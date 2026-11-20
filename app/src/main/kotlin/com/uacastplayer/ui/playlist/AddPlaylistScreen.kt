package com.uacastplayer.ui.playlist
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.appBackground
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.uacastplayer.ui.tv.tvFocus
import com.uacastplayer.ui.tv.TvDialogInputRegistration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.playlist.CleartextCredentialPolicy
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Feature
import com.uacastplayer.playlist.XtreamUrlBuilder
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.Title

/** A one-shot HTTP confirmation. HTTP playlist support remains available for providers that have
 * no TLS endpoint, but it must be an explicit user decision instead of an accidental credential
 * transfer over an untrusted network. */
private sealed interface PendingCleartextLoad {
    data class Url(val value: String) : PendingCleartextLoad
    data class Xtream(val server: String, val username: String, val password: String) : PendingCleartextLoad
}

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
    hasExistingSources: Boolean = playlistState.hasChannels,
) {
    var sourceType by rememberSaveable { mutableStateOf(PlaylistSourceType.URL) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val gate = LocalFeatureGate.current
    var xtreamServer by rememberSaveable { mutableStateOf("") }
    var xtreamUsername by rememberSaveable { mutableStateOf("") }
    // Credentials must not be written to the Activity saved-state Bundle. `rememberSaveable`
    // persists through process recreation and can therefore leave the password in system-managed
    // state; the field is intentionally cleared on recreation instead.
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var pendingCleartextLoad by remember { mutableStateOf<PendingCleartextLoad?>(null) }
    PlaylistAddCompletion(
        state = playlistState,
        onSubmit = { onSetDisplayName(effectiveDisplayName(name, sourceType, xtreamServer)) },
        onSaved = onPlaylistLoaded,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .appBackground(plain = true)
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

        AddPlaylistProviderHint(hasExistingSources)

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

        Text(
            text = stringResource(
                when (sourceType) {
                    PlaylistSourceType.URL -> R.string.add_playlist_method_url_help
                    PlaylistSourceType.FILE -> R.string.add_playlist_method_file_help
                    PlaylistSourceType.XTREAM -> R.string.add_playlist_method_xtream_help
                },
            ),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = GapS),
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

        PlaylistNameOptions(name) { name = it }

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

        PlaylistPersistenceStatus(
            state = playlistState.sourceSaveState,
            onRetry = { onSetDisplayName(effectiveDisplayName(name, sourceType, xtreamServer)) },
            retryEnabled = !playlistState.isLoading,
        )
        SourceActionButton(
            sourceType = sourceType,
            url = url,
            xtreamServer = xtreamServer,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword,
            isLoading = playlistState.isLoading ||
                playlistState.sourceSaveState == com.uacastplayer.playlist.PlaylistSourceSaveState.SAVING,
            onLoadUrl = { value ->
                if (CleartextCredentialPolicy.isCleartextPlaylistUrl(value)) {
                    pendingCleartextLoad = PendingCleartextLoad.Url(value)
                } else {
                    onLoadUrl(value)
                }
            },
            onLoadXtream = { server, username, password ->
                if (CleartextCredentialPolicy.exposesCredentials(server)) {
                    pendingCleartextLoad = PendingCleartextLoad.Xtream(server, username, password)
                } else {
                    onLoadXtream(server, username, password)
                }
            },
            onPickFile = onPickFile,
        )

        Spacer(modifier = Modifier.height(GapL))
    }

    pendingCleartextLoad?.let { pending ->
        CleartextLoadDialog(
            pending = pending,
            onDismiss = { pendingCleartextLoad = null },
            onConfirm = {
                pendingCleartextLoad = null
                dispatchCleartextLoad(it, onLoadUrl, onLoadXtream)
            },
        )
    }
}

@Composable
internal fun ProviderSourceHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GapM)
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = false,
            )
            .padding(CardPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = AppIcons.HelpCircle,
            contentDescription = null,
            tint = UaTheme.palette.accentText,
        )
        Column(modifier = Modifier.padding(start = GapM)) {
            Text(
                text = stringResource(R.string.add_playlist_source_title),
                style = SectionLabel,
                color = UaTheme.palette.labelPrimary,
            )
            Text(
                text = stringResource(R.string.add_playlist_tip),
                style = BodyText,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = GapS),
            )
        }
    }
}

@Composable
private fun CleartextLoadDialog(
    pending: PendingCleartextLoad,
    onDismiss: () -> Unit,
    onConfirm: (PendingCleartextLoad) -> Unit,
) {
    val message = when (pending) {
        is PendingCleartextLoad.Url -> R.string.add_playlist_url_cleartext_warning
        is PendingCleartextLoad.Xtream -> R.string.add_playlist_xtream_cleartext_warning
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_playlist_title)) },
        text = { TvDialogInputRegistration(); Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending) }, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun dispatchCleartextLoad(
    pending: PendingCleartextLoad,
    onLoadUrl: (String) -> Unit,
    onLoadXtream: (server: String, username: String, password: String) -> Unit,
) {
    when (pending) {
        is PendingCleartextLoad.Url -> onLoadUrl(pending.value)
        is PendingCleartextLoad.Xtream -> onLoadXtream(
            pending.server,
            pending.username,
            pending.password,
        )
    }
}

/** An Xtream source with no custom name defaults to its server host instead of falling through to
 * the fingerprint-based label the other two source types get from a blank name. */
private fun effectiveDisplayName(name: String, sourceType: PlaylistSourceType, xtreamServer: String): String =
    if (name.isBlank() && sourceType == PlaylistSourceType.XTREAM) XtreamUrlBuilder.serverHost(xtreamServer) else name


private fun hasLoadFeedback(playlistState: PlaylistUiState): Boolean =
    playlistState.isLoading ||
        playlistState.error != null ||
        (playlistState.activePlaylistId != null && !playlistState.hasChannels)

@Composable
private fun loadFeedbackMessage(playlistState: PlaylistUiState): String = when {
    playlistState.isLoading -> stringResource(R.string.add_playlist_status_loading)
    playlistState.error != null -> playlistState.error.asUserMessage()
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
