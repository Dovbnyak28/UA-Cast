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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme

private const val SPINNER_SIZE_DP = 18
private const val SPINNER_STROKE_DP = 2

/**
 * "Other devices (DLNA)" bottom sheet: runs [discoverDevices] once per appearance and lists what it
 * finds. There is no live-updating device list - an SSDP search is a fixed ~3s window (see
 * `dlna/SsdpDiscovery`), not a subscription, so re-opening the sheet is how the user retries.
 *
 * Separate from the Chromecast button next to it ([com.uacastplayer.ui.player.PlayerCastButton]),
 * because the two protocols reach different hardware: Cast covers Google devices, DLNA covers the
 * Samsung/LG/Sony sets that have no Cast receiver at all.
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = UaTheme.palette.surface2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHPadding)
                .padding(bottom = GapL),
            verticalArrangement = Arrangement.spacedBy(GapS),
        ) {
            Text(
                text = stringResource(R.string.dlna_sheet_title),
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
            )

            val connected = connectionState.connectedDevice
            connected?.let { device ->
                DlnaConnectedRow(deviceName = device.friendlyName, onStop = onStopCasting)
            }

            // The connected device is already the card above, so listing it again below it said the
            // same name twice in a row and offered a tap that would only re-point the renderer at
            // what it is already playing. What stays listed is what the user could switch *to*.
            val switchable = devices.filter { it != connected }
            when {
                searching -> DlnaSearchingRow()
                // Only when there is nothing at all. With a device connected, an empty remainder
                // means "nothing else to switch to", and "No devices found" directly under a card
                // naming a connected TV reads as a contradiction.
                switchable.isEmpty() && connected == null -> Text(
                    text = stringResource(R.string.dlna_sheet_no_devices),
                    style = BodyText,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(vertical = GapS),
                )
                else -> switchable.forEach { device ->
                    DlnaDeviceRow(device = device, onClick = { onDeviceSelected(device) })
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
        CircularProgressIndicator(
            modifier = Modifier.size(SPINNER_SIZE_DP.dp),
            strokeWidth = SPINNER_STROKE_DP.dp,
            color = UaTheme.palette.azure,
        )
        Text(
            text = stringResource(R.string.dlna_sheet_searching),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
        )
    }
}

@Composable
private fun DlnaConnectedRow(deviceName: String, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .background(UaTheme.palette.surface1)
            .padding(start = 14.dp, end = GapS, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Tv, contentDescription = null, tint = UaTheme.palette.azure)
        // weight(1f) is the whole fix for what this row used to look like. With SpaceBetween and an
        // unweighted Column, the text was measured at its full intrinsic width first and the button
        // got whatever was left - which for a real device name was almost nothing, so "Stop casting"
        // laid itself out one character per line and the card grew to half the sheet. Weighted, the
        // button is measured first at the width it actually needs and the name takes the remainder.
        Column(modifier = Modifier.weight(1f)) {
            // The name alone, not "Connected to <name>": the green line right below already says
            // this is a live cast, and the name was appearing a third time in the device list under
            // the card. Ellipsized because a friendlyName is whatever the TV's owner typed into it.
            Text(
                text = deviceName,
                style = BodyText,
                color = UaTheme.palette.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.cast_status_connected),
                style = Caption,
                color = UaTheme.palette.routeGreen,
            )
        }
        TextButton(onClick = onStop) {
            Text(
                text = stringResource(R.string.dlna_stop_casting),
                color = UaTheme.palette.azure,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DlnaDeviceRow(device: DlnaDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Tv, contentDescription = null, tint = UaTheme.palette.labelSecondary)
        Text(
            text = device.friendlyName,
            style = BodyText,
            color = UaTheme.palette.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The rows, not the sheet: ModalBottomSheet needs a real window to lay out against, which the
 * preview renderer does not give it. Covers the two states with content - connected, and a found
 * device - since searching/empty are a spinner and a line of text. */
@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun DlnaDeviceSheetRowsPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Column(verticalArrangement = Arrangement.spacedBy(GapS), modifier = Modifier.padding(ScreenHPadding)) {
            // The real friendlyName of the TV this layout was fixed against - a short placeholder
            // is exactly what hid the wrapping bug from this preview in the first place.
            DlnaConnectedRow(deviceName = "[TV] Samsung 6 Series (40)", onStop = {})
            DlnaDeviceRow(
                device = DlnaDevice(friendlyName = "LG webOS TV", controlUrl = "http://192.168.1.5/upnp/control"),
                onClick = {},
            )
            DlnaSearchingRow()
        }
    }
}
