package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title

/**
 * Long-press actions for a channel - see `GroupActionsSheet` for the group-level equivalent this
 * mirrors. [isLocked] picks between "Lock"/"Unlock" wording; unlocking is the only action that
 * bubbles up to a PIN prompt (see `app/ParentalControlController` - locking a channel is always
 * allowed, unlocking one requires the current session to already be PIN-unlocked).
 *
 * Favoriting lives here rather than on the tile itself: the grid's star was an IconButton whose
 * 48dp touch target overflowed a ~150dp tile, so a tap aimed at the channel toggled the favorite
 * instead of opening it (see `ChannelTile`). A long press is the same gesture that already reaches
 * lock/unlock, and it cannot be hit by accident while picking a channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelActionsSheet(
    channelName: String,
    isFavorite: Boolean,
    isLocked: Boolean,
    onOpenGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding).padding(bottom = GapM)) {
            Text(
                text = channelName,
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = GapM),
            )
            ChannelActionRow(
                label = stringResource(R.string.channels_channel_guide),
                onClick = { onOpenGuide(); onDismiss() },
            )
            ChannelActionRow(
                label = stringResource(
                    if (isFavorite) {
                        R.string.channels_channel_remove_favorite
                    } else {
                        R.string.channels_channel_add_favorite
                    },
                ),
                onClick = { onToggleFavorite(); onDismiss() },
            )
            ChannelActionRow(
                label = stringResource(
                    if (isLocked) R.string.channels_channel_unlock else R.string.channels_channel_lock,
                ),
                onClick = { onToggleLock(); onDismiss() },
            )
        }
    }
}

@Composable
private fun ChannelActionRow(label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(text = label, style = BodyText, color = UaTheme.palette.labelPrimary)
    }
}
