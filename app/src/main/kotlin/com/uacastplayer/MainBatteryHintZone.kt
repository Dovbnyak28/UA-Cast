package com.uacastplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.ui.components.BatteryOptimizationDialog

/** Collects only [AppViewModel.showBatteryOptimizationHint] - the rest of the app's state has
 * nothing to do with whether this dialog is showing. */
@Composable
internal fun BatteryHintZone(viewModel: AppViewModel) {
    val showBatteryOptimizationHint by viewModel.showBatteryOptimizationHint.collectAsStateWithLifecycle()
    if (showBatteryOptimizationHint) {
        BatteryOptimizationDialog(
            onAllow = viewModel::dismissBatteryOptimizationHint,
            onDismiss = viewModel::dismissBatteryOptimizationHint,
        )
    }
}

