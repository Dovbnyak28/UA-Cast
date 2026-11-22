package app.zorik.player.ui.channels
import app.zorik.player.ui.theme.UaTheme

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.Title

/**
 * Long-press actions for a group in the overview grid - see
 * `playlist/GroupOrderPolicy.kt`/`data/playlist/GroupVisibilityStore.kt`. [isPinned] picks between
 * "Pin"/"Unpin" wording; hiding is always offered (a pinned group can still be hidden - hidden wins
 * over pinned, see [app.zorik.player.playlist.GroupOrderPolicy.order]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionsSheet(
    groupLabel: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding).padding(bottom = GapM)) {
            Text(
                text = groupLabel,
                style = Title,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(bottom = GapM),
            )
            GroupActionRow(
                label = stringResource(if (isPinned) R.string.channels_group_unpin else R.string.channels_group_pin),
                onClick = { onTogglePin(); onDismiss() },
            )
            GroupActionRow(
                label = stringResource(R.string.channels_group_hide),
                onClick = { onHide(); onDismiss() },
            )
        }
    }
}

@Composable
private fun GroupActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(text = label, style = BodyText, color = UaTheme.palette.labelPrimary)
    }
}
