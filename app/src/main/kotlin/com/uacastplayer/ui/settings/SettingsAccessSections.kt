package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.channels.groupLabel
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SetPinDialog
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenGroupsSheet(
    groups: List<GroupedChannels>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.settings_hidden_groups, groups.size),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            for (grouped in groups) {
                val key = groupDisplayKey(grouped.group)
                AccessRow(
                    label = groupLabel(grouped.group),
                    actionLabel = stringResource(R.string.settings_hidden_groups_restore),
                    onClick = { onRestore(key) },
                )
            }
        }
    }
}

@Composable
internal fun ParentalControlSection(
    playlistState: PlaylistUiState,
    lockedChannelKeys: Set<String>,
    parentalControlPinSet: Boolean,
    onSetParentalControlPin: suspend (String) -> Boolean,
    onResetParentalControl: () -> Unit,
    onUnlockChannel: (M3uChannel) -> Unit,
    requireParentalControlUnlock: (() -> Unit) -> Unit,
) {
    val pinScope = rememberCoroutineScope()
    if (!parentalControlPinSet) {
        SetUpParentalControl(pinScope = pinScope, onSetParentalControlPin = onSetParentalControlPin)
        return
    }

    var showManageLocked by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    PlaylistActionRow(
        label = stringResource(R.string.parental_control_manage_locked, lockedChannelKeys.size),
        icon = AppIcons.Lock,
        onClick = { requireParentalControlUnlock { showManageLocked = true } },
    )
    PlaylistActionRow(
        label = stringResource(R.string.parental_control_change_pin),
        icon = AppIcons.Lock,
        onClick = { requireParentalControlUnlock { showChangePin = true } },
        modifier = Modifier.padding(top = 8.dp),
    )
    PlaylistActionRow(
        label = stringResource(R.string.parental_control_reset),
        icon = AppIcons.Delete,
        onClick = { showResetConfirm = true },
        modifier = Modifier.padding(top = 8.dp),
    )

    if (showManageLocked) {
        val lockedChannels by produceState(emptyList(), playlistState.channels, lockedChannelKeys) {
            value = withPlaylistCpu {
                playlistState.channels.filter { FavoriteKey.of(it) in lockedChannelKeys }
            }
        }
        LockedChannelsSheet(lockedChannels, onUnlockChannel) { showManageLocked = false }
    }
    if (showChangePin) {
        SetPinDialog(
            onSubmit = { pin ->
                pinScope.launch { if (onSetParentalControlPin(pin)) showChangePin = false }
            },
            onDismiss = { showChangePin = false },
        )
    }
    if (showResetConfirm) {
        ResetParentalControlConfirmDialog(
            onConfirm = {
                onResetParentalControl()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@Composable
private fun SetUpParentalControl(
    pinScope: kotlinx.coroutines.CoroutineScope,
    onSetParentalControlPin: suspend (String) -> Boolean,
) {
    var showSetPin by rememberSaveable { mutableStateOf(false) }
    PlaylistActionRow(
        label = stringResource(R.string.parental_control_set_up),
        icon = AppIcons.Lock,
        onClick = LocalFeatureGate.current.guard(Feature.PARENTAL_CONTROL) { showSetPin = true },
    )
    if (showSetPin) {
        SetPinDialog(
            onSubmit = { pin -> pinScope.launch { if (onSetParentalControlPin(pin)) showSetPin = false } },
            onDismiss = { showSetPin = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedChannelsSheet(
    channels: List<M3uChannel>,
    onUnlock: (M3uChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.parental_control_manage_locked, channels.size),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            for (channel in channels) {
                AccessRow(
                    label = channel.displayName,
                    actionLabel = stringResource(R.string.parental_control_unlock_action),
                    onClick = { onUnlock(channel) },
                )
            }
        }
    }
}

@Composable
private fun AccessRow(label: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(text = actionLabel, onClick = onClick)
    }
}

@Composable
private fun ResetParentalControlConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(stringResource(R.string.parental_control_reset_confirm_title)) },
        text = { Text(stringResource(R.string.parental_control_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.parental_control_reset), color = UaTheme.palette.routeRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
