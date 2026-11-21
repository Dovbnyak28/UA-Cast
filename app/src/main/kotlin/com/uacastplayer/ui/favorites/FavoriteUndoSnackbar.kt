package com.uacastplayer.ui.favorites

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R
import com.uacastplayer.ui.theme.UaTheme

@Composable
internal fun FavoriteUndoSnackbar(modifier: Modifier = Modifier) {
    val section by rememberUpdatedState(LocalFavoriteUndo.current)
    val notice = section.notice
    val host = remember { SnackbarHostState() }
    val message = notice?.let { stringResource(R.string.favorites_removed_message, it.displayName) }.orEmpty()
    val undoLabel = stringResource(R.string.common_undo)
    LaunchedEffect(notice?.token) {
        if (notice != null) {
            val result = host.showSnackbar(
                message, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) section.onUndo(notice.token)
            else section.onDismiss(notice.token)
        }
    }
    SnackbarHost(hostState = host, modifier = modifier) { data ->
        Snackbar(data, containerColor = UaTheme.palette.surface2, contentColor = UaTheme.palette.labelPrimary,
            actionColor = UaTheme.palette.azure, dismissActionContentColor = UaTheme.palette.labelSecondary)
    }
}
