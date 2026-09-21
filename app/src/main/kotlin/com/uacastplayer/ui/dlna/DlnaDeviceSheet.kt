package com.uacastplayer.ui.dlna

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.dlna.VolumeRange
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import kotlin.math.roundToInt

private const val SPINNER_SIZE_DP = 18
private const val SPINNER_STROKE_DP = 2

/**
 * "Other devices (DLNA)" bottom sheet: runs [discoverDevices] once per appearance and lists what it
 * finds. There is no live-updating device list - an SSDP search is a fixed ~3s window (see
 * `dlna/SsdpDiscovery`), not a subscription. The explicit search action starts a new window.
 *
 * Reached from the shared TV picker, separately from [com.uacastplayer.ui.player.PlayerCastButton],
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
    onVolumeChange: (Int) -> Unit,
) {
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchGeneration) {
        searching = true
        devices = discoverDevices()
        searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = UaTheme.palette.surface2) {
        DlnaDeviceSheetContent(
            connectionState = connectionState,
            devices = devices,
            searching = searching,
            onDeviceSelected = onDeviceSelected,
            onStopCasting = onStopCasting,
            onVolumeChange = onVolumeChange,
            onRetryDiscovery = { searchGeneration++ },
        )
    }
}

/**
 * Everything the sheet shows, without the sheet.
 *
 * Split out because [ModalBottomSheet] needs a real window to lay itself out against, which neither
 * the preview renderer nor a Compose test rule provides - so as long as the content lived inside it,
 * the decisions below (which device is listed, whether there is a volume control at all) could only
 * be checked by eye on a device.
 */
@Composable
internal fun DlnaDeviceSheetContent(
    connectionState: DlnaConnectionState,
    devices: List<DlnaDevice>,
    searching: Boolean,
    onDeviceSelected: (DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onRetryDiscovery: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
        connected?.takeUnless { connectionState.isConnecting }?.let { device ->
            DlnaConnectedRow(deviceName = device.friendlyName, onStop = onStopCasting)
            // Absent, not disabled, when the renderer has no RenderingControl service or the
            // first read failed: a greyed-out slider sitting at zero would say the TV is muted.
            connectionState.volume?.let { volume ->
                DlnaVolumeRow(volume = volume, onVolumeChange = onVolumeChange)
            }
        }

        // The connected device is already the card above, so listing it again below it said the
        // same name twice in a row and offered a tap that would only re-point the renderer at
        // what it is already playing. What stays listed is what the user could switch *to*.
        val switchable = devices.filter { it != connected }
        when {
            connectionState.isConnecting -> {
                Text(
                    stringResource(
                        R.string.dlna_connecting_device,
                        connectionState.connectingDevice?.friendlyName.orEmpty(),
                    ),
                    style = BodyText,
                    color = UaTheme.palette.labelPrimary,
                )
                TextButton(onClick = onStopCasting) { Text(stringResource(R.string.common_cancel)) }
            }
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
            // Discovery, and the list itself, stay free: a user who never sees their TV listed has
            // no way to learn the app can reach it. Connecting to one is the sold act.
            else -> {
                val gate = LocalFeatureGate.current
                switchable.forEach { device ->
                    DlnaDeviceRow(
                        device = device,
                        onClick = gate.guard(Feature.DLNA) { onDeviceSelected(device) },
                    )
                }
            }
        }
        connectionState.failedDevice?.let { device ->
            Text(
                stringResource(R.string.dlna_connection_failed, device.friendlyName),
                style = BodyText,
                color = UaTheme.palette.labelPrimary,
            )
            val gate = LocalFeatureGate.current
            TextButton(onClick = gate.guard(Feature.DLNA) { onDeviceSelected(device) }) {
                Text(stringResource(R.string.common_retry))
            }
        }
        if (!searching && !connectionState.isConnecting) {
            Text(stringResource(R.string.dlna_network_hint), style = Caption, color = UaTheme.palette.labelSecondary)
            TextButton(onClick = onRetryDiscovery) { Text(stringResource(R.string.dlna_search_again)) }
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

/**
 * The renderer's own volume, on the renderer's own scale - not the phone's. Casting hands the audio
 * to the TV, so the phone's volume keys control nothing, and the remote was the only way to turn it
 * down until this row existed.
 *
 * Only the release of a drag is sent. A `SetVolume` per pixel would be a SOAP round trip per frame
 * at a renderer that answers them one at a time, which is how a drag becomes a two-second freeze
 * followed by a burst of stale actions.
 */
@Composable
private fun DlnaVolumeRow(volume: Int, onVolumeChange: (Int) -> Unit) {
    // Where the finger is, while the finger is down. Retired by the repository publishing a volume -
    // it publishes the requested value immediately and the renderer's reported one a round trip
    // later (see DlnaSessionRepository.setVolume), so the thumb neither snaps back nor holds a value
    // the TV refused.
    var dragged by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(volume) { dragged = null }
    val shown = dragged ?: volume.toFloat()
    val label = stringResource(R.string.dlna_volume)
    val valueLabel = stringResource(R.string.dlna_volume_value, shown.roundToInt())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .background(UaTheme.palette.surface1)
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(GapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Volume,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = BodyText,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = Caption,
                color = UaTheme.palette.azure,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(UaTheme.palette.surface2)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Slider(
            value = shown,
            onValueChange = { dragged = it },
            onValueChangeFinished = { dragged?.let { onVolumeChange(it.roundToInt()) } },
            valueRange = VolumeRange.MIN.toFloat()..VolumeRange.MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = UaTheme.palette.azure,
                activeTrackColor = UaTheme.palette.azure,
                inactiveTrackColor = UaTheme.palette.overlayHighlight,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // Keep the visual track compact while preserving the full 48dp touch target.
                .height(48.dp)
                .padding(bottom = 2.dp)
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun DlnaDeviceRow(device: DlnaDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusItem))
            .clickable(role = Role.Button, onClickLabel = device.friendlyName, onClick = onClick)
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
            DlnaVolumeRow(volume = 23, onVolumeChange = {})
            DlnaDeviceRow(
                device = DlnaDevice(friendlyName = "LG webOS TV", controlUrl = "http://192.168.1.5/upnp/control"),
                onClick = {},
            )
            DlnaSearchingRow()
        }
    }
}
