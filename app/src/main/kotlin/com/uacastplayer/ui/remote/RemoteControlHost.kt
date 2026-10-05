package com.uacastplayer.ui.remote

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.R
import com.uacastplayer.remote.PhoneRemoteViewModel
import com.uacastplayer.remote.TvRemoteReceiverViewModel
import com.uacastplayer.remote.TvRemoteState
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.tv.TvDialogInputRegistration

val LocalOpenRemote = staticCompositionLocalOf<() -> Unit> { {} }
enum class PhoneRemoteMode { REMOTE, TOUCHPAD }

/** The local listener is opt-in and exists only while the TV Activity is visible. */
@Composable
fun RemoteControlHost(
    television: Boolean,
    phone: PhoneRemoteViewModel,
    receiver: TvRemoteReceiverViewModel,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<PhoneRemoteMode?>(null) }
    val tvState by receiver.state.collectAsStateWithLifecycle()
    val phoneState by phone.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, phone, receiver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                open = false
                phone.disconnect()
                receiver.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tvState.paired) { if (television && tvState.paired) open = false }
    CompositionLocalProvider(LocalOpenRemote provides {
        open = true
        mode = null
        if (television && tvState.endpoint == null && !tvState.starting) receiver.start()
    }) { content() }
    if (!open) return
    val dismiss = {
        open = false
        if (television) { if (!tvState.paired) receiver.stop() } else phone.disconnect()
    }
    when {
        television -> TvPairingDialog(tvState, receiver::start, dismiss)
        mode == null -> RemoteModeChooser({ mode = it }, dismiss)
        else -> PhoneRemoteDialog(checkNotNull(mode), { mode = it }, phoneState, phone::connect, phone::send, dismiss)
    }
}

@Composable
private fun TvPairingDialog(tvState: TvRemoteState, onNewCode: () -> Unit, dismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.tv_pair_phone)) },
            text = {
                TvDialogInputRegistration()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.remote_pair_tv_help))
                    when {
                        tvState.starting -> Text(stringResource(R.string.remote_connecting))
                        tvState.failed -> Text(stringResource(R.string.remote_tv_network_error))
                        tvState.paired -> Text(stringResource(R.string.remote_connected))
                        else -> {
                            Text(stringResource(R.string.remote_tv_address, tvState.endpoint.toString()))
                            Text(stringResource(R.string.remote_tv_code, tvState.code.orEmpty()))
                            Text(stringResource(R.string.remote_code_expiry))
                        }
                    }
                }
            },
            confirmButton = {
                SecondaryButton(stringResource(R.string.remote_new_code), onNewCode, enabled = !tvState.starting)
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
            },
        )
}

@Composable
internal fun RemoteModeChooser(onSelect: (PhoneRemoteMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.remote_scope_help))
                PrimaryButton(stringResource(R.string.remote_mode_buttons), { onSelect(PhoneRemoteMode.REMOTE) },
                    Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.remote_mode_touchpad), { onSelect(PhoneRemoteMode.TOUCHPAD) },
                    Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
