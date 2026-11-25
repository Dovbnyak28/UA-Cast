package app.zorik.player.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyRegular
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.TouchTargetMin
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.tv.LocalTvMode
import app.zorik.player.ui.tv.tvFocus

@Composable
internal fun RemoteHeaderAction(labelled: Boolean) {
    val state = LocalRemoteHeaderState.current
    // On a phone the remote icon sat in every screen's header from the first launch, where it only
    // confused people who had never paired a TV. It is started from Settings now
    // (RemoteSettingsEntry) and appears here only while a session is live, to show its status and
    // reopen it. On a TV this is "Pair phone", the receiver's own entry point, and always shows.
    if (!LocalTvMode.current && !state.connected && !state.connecting && !state.failed) return
    val label = stringResource(if (LocalTvMode.current) R.string.tv_pair_phone else R.string.remote_title)
    val status = when {
        state.connected -> stringResource(R.string.remote_connected)
        state.connecting -> stringResource(R.string.remote_connecting)
        state.failed -> stringResource(R.string.remote_connection_error)
        else -> ""
    }
    val semantics = Modifier.tvFocus().semantics { contentDescription = label; stateDescription = status }
    if (labelled) TextButton(onClick = LocalOpenRemote.current, modifier = semantics.widthIn(max = 128.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteStatusIcon(state)
                Text(stringResource(if (LocalTvMode.current) R.string.tv_pair_phone else R.string.remote_short_title),
                    style = BodyRegular, color = UaTheme.palette.azure, maxLines = 2)
            }
            if (state.connected) Text(state.address ?: status, style = Caption, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = UaTheme.palette.labelSecondary)
        }
    } else IconButton(onClick = LocalOpenRemote.current, modifier = semantics.size(TouchTargetMin)) {
        RemoteStatusIcon(state)
    }
}

@Composable
private fun RemoteStatusIcon(state: RemoteHeaderState) {
    Box {
        Icon(AppIcons.Remote, null, tint = UaTheme.palette.azure, modifier = Modifier.size(24.dp))
        if (state.connected || state.failed) Box(Modifier.align(Alignment.TopEnd).size(7.dp)
            .background(if (state.connected) UaTheme.palette.routeGreen else UaTheme.palette.routeAmber, CircleShape))
    }
}

/** The phone's way into the TV remote: a Settings card, since the header no longer carries it. */
@Composable
internal fun RemoteSettingsEntry() {
    if (LocalTvMode.current) return
    app.zorik.player.ui.settings.SettingsNavigationRow(
        title = stringResource(R.string.remote_title),
        subtitle = stringResource(R.string.settings_remote_summary),
        icon = AppIcons.Remote,
        onClick = LocalOpenRemote.current,
    )
}
