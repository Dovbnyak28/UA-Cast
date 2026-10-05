package com.uacastplayer.ui.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.UaTheme

/** Destructive and reorder controls are revealed intentionally, never beside every normal Play target. */
@Composable
internal fun FavoriteEditActions(
    name: String,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    var showMoveMenu by rememberSaveable { mutableStateOf(false) }
    Row {
        if (onMoveUp != null || onMoveDown != null) {
            Box {
                IconButton(onClick = { showMoveMenu = true }) {
                    Icon(AppIcons.Sort, contentDescription = stringResource(R.string.favorites_sort_manual))
                }
                DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                    listOf(R.string.favorites_move_up to onMoveUp, R.string.favorites_move_down to onMoveDown)
                        .forEach { (label, action) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                enabled = action != null,
                                onClick = { showMoveMenu = false; action?.invoke() },
                            )
                        }
                }
            }
        }
        IconButton(onClick = { confirmRemoval = true }) {
            Icon(
                AppIcons.Delete,
                contentDescription = stringResource(R.string.favorites_remove_content_description),
                tint = UaTheme.palette.routeRed,
            )
        }
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            text = { Text(stringResource(R.string.favorites_remove_confirm, name)) },
            confirmButton = {
                TextButton(onClick = { confirmRemoval = false; onRemove() }) {
                    Text(stringResource(R.string.favorites_remove_content_description))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
