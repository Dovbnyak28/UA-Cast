package app.zorik.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import app.zorik.player.ui.components.CastPeerIconGlyphSize
import app.zorik.player.ui.theme.raisedSurface
import app.zorik.player.ui.theme.CaptionSemibold
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import app.zorik.player.R
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.IconButtonSize
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.components.SmallRoundIconButton
import app.zorik.player.ui.tv.tvFocus

/** Chromecast retains its SDK-owned button and chooser; this label opens the DLNA chooser. */
@Composable
internal fun OtherTvButton(
    connected: Boolean,
    onClick: () -> Unit,
    background: Color = UaTheme.palette.scrimBackground,
) {
    val description = stringResource(R.string.dlna_sheet_title)
    val tint = if (connected) UaTheme.palette.azure else UaTheme.palette.labelPrimary
    if (LocalCompactPlayerControls.current || LocalDensity.current.fontScale >= DEVICE_ICON_FONT_SCALE) {
        SmallRoundIconButton(AppIcons.Tv, onClick, contentDescription = description, tint = tint,
            background = background, iconSize = CastPeerIconGlyphSize)
        return
    }
    // A pill on the same surface and at the same 48dp height as the round buttons beside it, with
    // a TV glyph, instead of bare text: the header reads as one set of controls. 48dp is also the
    // touch target CinemaPlayerInstrumentedTest asserts on a device.
    Row(
        modifier = Modifier
            .heightIn(min = IconButtonSize)
            .widthIn(max = OTHER_TV_MAX_WIDTH)
            .raisedSurface(CircleShape, background, shadow = false)
            .tvFocus(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.Tv, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.player_other_tvs), style = CaptionSemibold, color = tint,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val OTHER_TV_MAX_WIDTH = 128.dp

private const val DEVICE_ICON_FONT_SCALE = 1.5f

@Composable
internal fun ConnectedReceiverRow(content: PlayerScreenContent, actions: PlayerScreenActions) {
    val dlna = content.dlnaState.connectedDevice
    val name = dlna?.friendlyName ?: content.uiState.castReceiverName ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Caption, color = UaTheme.palette.labelSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(modifier = Modifier.heightIn(min = IconButtonSize), onClick = {
            if (dlna != null) actions.viewModel.dlna.stop() else actions.viewModel.castControls.stop()
        }) {
            Text(stringResource(R.string.player_stop_casting))
        }
    }
}
