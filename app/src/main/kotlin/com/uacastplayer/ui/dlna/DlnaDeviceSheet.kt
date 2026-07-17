package com.uacastplayer.ui.dlna

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Azure
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.LabelPrimary
import com.uacastplayer.ui.theme.LabelSecondary
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RouteGreen
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Surface1
import com.uacastplayer.ui.theme.Surface2

/**
 * "Other devices (DLNA)" bottom sheet - runs [discoverDevices] once per appearance and lists what
 * it finds. No live device-list refresh: discovery completes in ~3s (see `dlna/SsdpDiscovery.kt`),
 * a manual re-open of the sheet is how the user retries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlnaDeviceSheet(
    connectionState: DlnaConnectionState,
    discoverDevices: suspend () -> List<DlnaDevice>,
    onDismiss: () -> Unit,
    onDeviceSelected: (DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
) {
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        searching = true
        devices = discoverDevices()
        searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHPadding)
                .padding(bottom = GapL),
            verticalArrangement = Arrangement.spacedBy(GapS),
        ) {
            Text(text = stringResource(R.string.dlna_sheet_title), style = CardTitle, color = LabelPrimary)

            connectionState.connectedDevice?.let { device ->
                DlnaConnectedRow(deviceName = device.friendlyName, onStop = onStopCasting)
            }

            when {
                searching -> DlnaSearchingRow()
                devices.isEmpty() -> Text(
                    text = stringResource(R.string.dlna_sheet_no_devices),
                    style = BodyText,
                    color = LabelSecondary,
                    modifier = Modifier.padding(vertical = GapS),
                )
                else -> devices.forEach { device ->
                    DlnaDeviceRow(
                        device = device,
                        isConnected = device == connectionState.connectedDevice,
                        onClick = { onDeviceSelected(device) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DlnaSearchingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = GapS),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Azure)
        Text(text = stringResource(R.string.dlna_sheet_searching), style = BodyText, color = LabelSecondary)
    }
}

@Composable
private fun DlnaConnectedRow(deviceName: String, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .background(Surface1)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.dlna_sheet_connected_to, deviceName),
                style = BodyText,
                color = LabelPrimary,
            )
            Text(text = stringResource(R.string.cast_status_connected), style = Caption, color = RouteGreen)
        }
        TextButton(onClick = onStop) {
            Text(text = stringResource(R.string.dlna_stop_casting), color = Azure)
        }
    }
}

@Composable
private fun DlnaDeviceRow(device: DlnaDevice, isConnected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Tv, contentDescription = null, tint = if (isConnected) Azure else LabelSecondary)
        Text(text = device.friendlyName, style = BodyText, color = LabelPrimary)
    }
}
