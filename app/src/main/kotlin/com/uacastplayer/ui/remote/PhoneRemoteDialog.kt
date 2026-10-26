package com.uacastplayer.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.core.remote.TouchpadNavigation
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.UaTheme

private const val PAIRING_CODE_DIGITS = 8
private const val TOUCHPAD_ASPECT_RATIO = 1.4f
private const val UP_ARROW_ROTATION = 90f
private const val RIGHT_ARROW_ROTATION = 180f
private const val DOWN_ARROW_ROTATION = 270f

@Composable
internal fun PhoneRemoteDialog(
    mode: PhoneRemoteMode,
    onMode: (PhoneRemoteMode) -> Unit,
    state: PhoneRemoteState,
    onConnect: (String, String) -> Unit,
    onCommand: (RemoteCommand) -> Boolean,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }
    // A pairing secret does not belong in a saved Bundle, preferences or diagnostic logs.
    var code by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = UaTheme.palette.surface1,
                modifier = Modifier.widthIn(max = 460.dp).testTag("phone_remote_dialog")) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.remote_title), style = Title, color = UaTheme.palette.labelPrimary)
                    RemoteModeSelector(mode, onMode)
                    if (state.connected) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Check, null, tint = UaTheme.palette.routeGreen,
                                modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.remote_connected), style = CaptionSemibold,
                                color = UaTheme.palette.labelPrimary, modifier = Modifier.weight(1f))
                        }
                        RemoteNavigationControls(mode, onCommand)
                    } else {
                        Text(stringResource(R.string.remote_pair_phone_help))
                        OutlinedTextField(address, { address = it }, singleLine = true, colors = uaTextFieldColors(),
                            label = { Text(stringResource(R.string.remote_address_label)) },
                            placeholder = { Text("192.168.1.10:12345") }, enabled = !state.connecting,
                            modifier = Modifier.fillMaxWidth().testTag("remote_address"))
                        OutlinedTextField(code, {
                            if (it.length <= PAIRING_CODE_DIGITS && it.all(Char::isDigit)) code = it
                        },
                            singleLine = true, colors = uaTextFieldColors(), enabled = !state.connecting,
                            label = { Text(stringResource(R.string.remote_code_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("remote_code"))
                        if (state.failed) {
                            Text(stringResource(R.string.remote_connection_error), color = UaTheme.palette.azure)
                        }
                        val connectLabel = if (state.connecting) R.string.remote_connecting else R.string.remote_connect
                        PrimaryButton(stringResource(connectLabel),
                            { onConnect(address, code) }, Modifier.fillMaxWidth(), enabled = !state.connecting)
                    }
                    SecondaryButton(stringResource(R.string.common_close), onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun RemoteNavigationControls(mode: PhoneRemoteMode, onCommand: (RemoteCommand) -> Boolean) {
    if (mode == PhoneRemoteMode.TOUCHPAD) RemoteTouchpad(onCommand) else RemoteDpad(onCommand)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton(RemoteCommand.BACK, stringResource(R.string.remote_back), onCommand, Modifier.weight(1f))
        RemoteButton(RemoteCommand.PLAY_PAUSE, stringResource(R.string.remote_play_pause), onCommand,
            Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton(RemoteCommand.PREVIOUS, stringResource(R.string.remote_previous_channel), onCommand,
            Modifier.weight(1f))
        RemoteButton(RemoteCommand.NEXT, stringResource(R.string.remote_next_channel), onCommand, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton(RemoteCommand.VOLUME_DOWN, stringResource(R.string.remote_volume_down), onCommand,
            Modifier.weight(1f))
        RemoteButton(RemoteCommand.VOLUME_UP, stringResource(R.string.remote_volume_up), onCommand, Modifier.weight(1f))
    }
}

@Composable
private fun RemoteDpad(onCommand: (RemoteCommand) -> Boolean) {
    val selectLabel = stringResource(R.string.remote_select)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteDirectionButton(RemoteCommand.UP, stringResource(R.string.remote_up), UP_ARROW_ROTATION, onCommand)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteDirectionButton(RemoteCommand.LEFT, stringResource(R.string.remote_left), 0f, onCommand)
            PrimaryButton("OK", { onCommand(RemoteCommand.SELECT) }, Modifier.size(72.dp)
                .testTag("remote_SELECT").semantics { contentDescription = selectLabel; role = Role.Button })
            RemoteDirectionButton(RemoteCommand.RIGHT, stringResource(R.string.remote_right), RIGHT_ARROW_ROTATION,
                onCommand)
        }
        RemoteDirectionButton(RemoteCommand.DOWN, stringResource(R.string.remote_down), DOWN_ARROW_ROTATION, onCommand)
    }
}

@Composable
private fun RemoteDirectionButton(command: RemoteCommand, label: String, rotation: Float,
    onCommand: (RemoteCommand) -> Boolean) {
    SmallRoundIconButton(AppIcons.ArrowBack, { onCommand(command) },
        Modifier.size(72.dp).graphicsLayer { rotationZ = rotation }.testTag("remote_${command.name}"),
        contentDescription = label, iconSize = 30.dp)
}

@Composable
private fun RemoteButton(command: RemoteCommand, label: String, onCommand: (RemoteCommand) -> Boolean,
    modifier: Modifier = Modifier) {
    val description = when (command) {
        RemoteCommand.PREVIOUS -> stringResource(R.string.player_previous)
        RemoteCommand.NEXT -> stringResource(R.string.player_next)
        else -> label
    }
    SecondaryButton(label, { onCommand(command) }, modifier.testTag("remote_${command.name}")
        .semantics { contentDescription = description })
}

@Composable
private fun RemoteTouchpad(onCommand: (RemoteCommand) -> Boolean) {
    val threshold = with(LocalDensity.current) { 36.dp.toPx() }
    val navigation = remember(threshold) { TouchpadNavigation(threshold) }
    val hint = stringResource(R.string.remote_touchpad_hint)
    val select = stringResource(R.string.remote_select)
    val directions = listOf(stringResource(R.string.remote_up) to RemoteCommand.UP,
        stringResource(R.string.remote_down) to RemoteCommand.DOWN,
        stringResource(R.string.remote_left) to RemoteCommand.LEFT,
        stringResource(R.string.remote_right) to RemoteCommand.RIGHT)
    Box(Modifier.fillMaxWidth().aspectRatio(TOUCHPAD_ASPECT_RATIO)
        .background(UaTheme.palette.void, RoundedCornerShape(20.dp))
        .border(1.dp, UaTheme.palette.edgeHighlightNeutral, RoundedCornerShape(20.dp))
        .testTag("remote_touchpad")
        .semantics(mergeDescendants = true) {
            contentDescription = hint
            role = Role.Button
            onClick(label = select) { onCommand(RemoteCommand.SELECT) }
            customActions = directions.map { (label, command) ->
                CustomAccessibilityAction(label) { onCommand(command) }
            }
        }
        .pointerInput(onCommand, navigation) {
            detectTapGestures { onCommand(RemoteCommand.SELECT) }
        }.pointerInput(onCommand, navigation) {
            detectDragGestures(onDragStart = { navigation.reset() }, onDragEnd = navigation::reset,
                onDragCancel = navigation::reset) { change, drag ->
                change.consume()
                navigation.drag(drag.x, drag.y)?.let(onCommand)
            }
        }.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(hint, color = UaTheme.palette.labelPrimary, modifier = Modifier.clearAndSetSemantics {})
    }
}
