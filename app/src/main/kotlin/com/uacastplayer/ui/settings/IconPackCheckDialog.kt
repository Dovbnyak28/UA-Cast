package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.uacastplayer.R
import com.uacastplayer.data.icons.IconPackChecker
import com.uacastplayer.data.icons.IconPackCheckResult
import com.uacastplayer.data.icons.IconPackSampleStatus
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.TvDialogInputRegistration
import com.uacastplayer.ui.tv.tvFocus
import kotlinx.coroutines.awaitCancellation

val LocalIconPackCheck = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
internal fun rememberIconPackCheckAction(channels: List<M3uChannel>): (String) -> Unit {
    var selected by remember { mutableStateOf<String?>(null) }
    selected?.let { source -> IconPackCheckDialog(source, channels, onDismiss = { selected = null }) }
    return { selected = it }
}

@Composable
internal fun IconPackCheckDialog(source: String, channels: List<M3uChannel>, onDismiss: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current.applicationContext
    var result by remember(source, channels) { mutableStateOf<IconPackCheckResult?>(null) }
    LaunchedEffect(source, channels, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (result == null) result = IconPackChecker(context).check(source, channels)
            awaitCancellation()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        title = { Text(stringResource(R.string.icon_pack_check_title)) },
        text = {
            TvDialogInputRegistration()
            val checked = result
            if (checked == null) CircularProgressIndicator(color = UaTheme.palette.azure)
            else IconPackCheckContent(checked)
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
internal fun IconPackCheckContent(result: IconPackCheckResult) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(
            R.string.icon_pack_check_summary,
            result.samples.count { it.status == IconPackSampleStatus.FOUND },
            result.samples.size, result.plan.missingIds,
        ))
        Text(stringResource(R.string.icon_pack_check_hint))
        if (result.plan.total == 0) Text(stringResource(R.string.icon_pack_check_no_channels))
        result.samples.forEach { sample ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sample.bytes?.let { AsyncImage(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                Column {
                    Text(sample.title)
                    Text(stringResource(sample.status.label()))
                }
            }
        }
    }
}

private fun IconPackSampleStatus.label(): Int = when (this) {
    IconPackSampleStatus.FOUND -> R.string.icon_pack_check_found
    IconPackSampleStatus.MISSING -> R.string.icon_pack_check_missing
    IconPackSampleStatus.NETWORK -> R.string.icon_pack_check_network
    IconPackSampleStatus.INVALID_IMAGE -> R.string.icon_pack_check_invalid
    IconPackSampleStatus.TOO_LARGE -> R.string.icon_pack_check_large
}
