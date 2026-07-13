package com.uacastplayer.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.uacastplayer.R

/**
 * One-time (see [com.uacastplayer.AppViewModel.showBatteryOptimizationHint]) explainer shown the
 * first time a Cast session connects, if the OS isn't already ignoring battery optimizations for
 * this app - aggressive manufacturer battery managers (MIUI/HyperOS and friends) otherwise kill
 * the cast proxy in the background. Also reachable manually from Settings.
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
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
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
