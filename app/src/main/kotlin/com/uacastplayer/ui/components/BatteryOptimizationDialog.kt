package com.uacastplayer.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.uacastplayer.R
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.UaCastTheme

private const val TAG = "BatteryOptimizationDialog"

/**
 * One-time (see [com.uacastplayer.AppViewModel.showBatteryOptimizationHint]) explainer shown the
 * first time a Cast session connects, if the OS isn't already ignoring battery optimizations for
 * this app - aggressive manufacturer battery managers (MIUI/HyperOS and friends) otherwise kill
 * the cast proxy in the background. Also reachable manually from Settings.
 *
 * Opens the general battery-optimization app list (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
 * rather than the direct per-package request (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS): the
 * direct request requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS manifest permission, which
 * Google Play requires justifying at publish time, and without it the OS silently drops the
 * request. The general list needs no extra permission at the cost of the user having to find this
 * app in it themselves.
 */
@Composable
fun BatteryOptimizationDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_hint_title)) },
        text = { Text(stringResource(R.string.battery_hint_body)) },
        confirmButton = {
            TextButton(onClick = {
                // ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS is documented, not guaranteed: a
                // Settings app that does not export it resolves nothing, and startActivity on an
                // unresolvable intent throws. This dialog appears on the first cast session, so
                // unguarded it would crash the app in the middle of its main feature, over a hint
                // nobody asked for. Same guard as sendDiagnostics and the release-page link.
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    AppLog.w(TAG) { "no battery optimization settings screen on this device" }
                }
                // Retired either way. The hint is one-shot, and a device that cannot open the
                // screen is precisely the one that must not be asked about it again on every
                // single cast session.
                onAllow()
            }) {
                Text(stringResource(R.string.battery_hint_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_hint_later))
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun BatteryOptimizationDialogPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        BatteryOptimizationDialog(onAllow = {}, onDismiss = {})
    }
}
