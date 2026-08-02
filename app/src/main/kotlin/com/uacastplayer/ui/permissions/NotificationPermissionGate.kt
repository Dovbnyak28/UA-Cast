package com.uacastplayer.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for `POST_NOTIFICATIONS` the first time [castConnected] goes true, and never again in this
 * process.
 *
 * `CastProxyService` is a foreground service that posts a notification and holds a partial wake lock
 * plus a Wi-Fi lock for the whole cast. From API 33 the notification is silently dropped without
 * this permission - the service still runs, so the user is left with something keeping their CPU
 * awake that they can neither see nor stop from outside the app. The manifest declaration alone is
 * not enough; the runtime grant is what makes the notification appear.
 *
 * Tied to a cast actually starting rather than to app launch on purpose. A permission prompt on
 * first launch has no visible reason attached to it and is the one users reflexively deny - and a
 * denial is close to permanent, since from API 33 the system stops showing the dialog after two
 * refusals. Asked at the moment a cast begins, the notification that appears a second later is the
 * answer to why it was asked.
 */
@Composable
fun NotificationPermissionGate(castConnected: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    // Saveable so a configuration change mid-prompt doesn't queue a second one behind the first.
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The launcher is recreated across recomposition; capturing it in the effect below by value
    // would pin whichever instance existed when the effect started.
    val currentLauncher by rememberUpdatedState(launcher)

    val granted = remember(castConnected) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(castConnected, granted) {
        if (!castConnected || granted || alreadyAsked) return@LaunchedEffect
        alreadyAsked = true
        currentLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
