package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.uacastplayer.R
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.tv.tvFocus

/** Chromecast retains its SDK-owned button and chooser; this label opens the DLNA chooser. */
@Composable
internal fun OtherTvButton(connected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.dlna_sheet_title)
    if (LocalCompactPlayerControls.current || LocalDensity.current.fontScale >= DEVICE_ICON_FONT_SCALE) {
        SmallRoundIconButton(AppIcons.Tv, onClick, contentDescription = description,
            tint = if (connected) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
            background = UaTheme.palette.scrimBackground)
        return
    }
    TextButton(onClick = onClick, modifier = Modifier.widthIn(max = 88.dp).tvFocus()
        .semantics { contentDescription = description }) {
        Text(stringResource(R.string.player_other_tvs), maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (connected) UaTheme.palette.azure else UaTheme.palette.labelPrimary)
    }
}

private const val DEVICE_ICON_FONT_SCALE = 1.5f

@Composable
internal fun ConnectedReceiverRow(content: PlayerScreenContent, actions: PlayerScreenActions) {
    val dlna = content.dlnaState.connectedDevice
    val name = dlna?.friendlyName ?: content.uiState.castReceiverName ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Caption, color = UaTheme.palette.labelSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            if (dlna != null) actions.viewModel.dlna.stop() else actions.viewModel.castControls.stop()
        }) {
            Text(stringResource(R.string.player_stop_casting))
        }
    }
}
