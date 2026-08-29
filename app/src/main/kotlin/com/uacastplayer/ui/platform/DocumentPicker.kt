package com.uacastplayer.ui.platform

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import com.uacastplayer.log.AppLog

private const val TAG = "DocumentPicker"

/**
 * Opens a document picker, on a device that may not have one.
 *
 * `ACTION_OPEN_DOCUMENT` and `ACTION_CREATE_DOCUMENT` - what `OpenDocument`/`CreateDocument` are
 * underneath - are the Storage Access Framework, which is a *package* (`DocumentsUI`) rather than
 * part of the platform. A ROM built without it resolves neither action; so does a managed profile
 * whose policy disables it, and Android TV, which this app can already be sideloaded onto even
 * though it stays off the leanback launcher (see docs/TV_SUPPORT.md) - a TV box is where an IPTV
 * player ends up. `ActivityResultLauncher.launch` reaches `startActivityForResult`, so nothing
 * resolving means an unchecked `ActivityNotFoundException`, thrown from the tap handler on the main
 * thread, taking the app down.
 *
 * The same guard, and the same reasoning, as `BatteryOptimizationDialog`, `openInstallPermissionSettings`
 * and `sendDiagnostics` already carry: a screen that cannot be opened is a screen not opened, never
 * a crash. What is left is a button that does nothing, which is a poor answer and a much better one
 * than the alternative - and on the playlist screen it is not the only way in, since a URL or an
 * Xtream login reach the same place without the picker.
 */
fun <I> ActivityResultLauncher<I>.launchOrLogAbsence(input: I, what: String) {
    try {
        launch(input)
    } catch (e: ActivityNotFoundException) {
        AppLog.w(TAG) { "This device has no document picker to $what with: ${e.javaClass.simpleName}" }
    }
}
