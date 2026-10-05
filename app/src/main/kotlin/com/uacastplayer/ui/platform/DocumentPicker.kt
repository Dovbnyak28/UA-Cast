package com.uacastplayer.ui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.uacastplayer.R
import androidx.activity.result.ActivityResultLauncher
import com.uacastplayer.log.AppLog

private const val TAG = "DocumentPicker"

/**
 * Opens a document picker, on a device that may not have one.
 *
 * `ACTION_OPEN_DOCUMENT` and `ACTION_CREATE_DOCUMENT` - what `OpenDocument`/`CreateDocument` are
 * underneath - are the Storage Access Framework, which is a *package* (`DocumentsUI`) rather than
 * part of the platform. A ROM built without it resolves neither action; so does a managed profile
 * whose policy disables it, and some Android TV ROMs. `ActivityResultLauncher.launch` reaches
 * `startActivityForResult`, so nothing
 * resolving means an unchecked `ActivityNotFoundException`, thrown from the tap handler on the main
 * thread, taking the app down.
 *
 * The same guard, and the same reasoning, as `BatteryOptimizationDialog`, `openInstallPermissionSettings`
 * and `sendDiagnostics` already carry: a screen that cannot be opened is a screen not opened, never
 * a crash. Returns availability so callers can report a blocked action; backup uses a visible
 * message. The playlist screen also offers URL and Xtream sources without a document picker.
 */
fun <I> ActivityResultLauncher<I>.launchOrLogAbsence(input: I, what: String): Boolean =
    try {
        launch(input)
        true
    } catch (e: ActivityNotFoundException) {
        AppLog.w(TAG) { "This device has no document picker to $what with: ${e.javaClass.simpleName}" }
        false
    } catch (e: SecurityException) {
        // A picker can be present but blocked by device policy or an OEM export restriction.
        AppLog.w(TAG) { "This device denied the document picker to $what with: ${e.javaClass.simpleName}" }
        false
    }

fun <I> ActivityResultLauncher<I>.launchBackupPicker(input: I, what: String, context: Context) {
    if (!launchBackupPickerIfSupported(input, what, context)) {
        Toast.makeText(context, R.string.settings_data_picker_unavailable, Toast.LENGTH_LONG).show()
    }
}

/** This exact OEM handler returns RESULT_CANCELED immediately without opening a picker.
 * Unknown/third-party handlers still launch normally; an absent resolver keeps the exception guard. */
internal fun <I> ActivityResultLauncher<I>.launchBackupPickerIfSupported(
    input: I, what: String, context: Context,
): Boolean {
    val handler = context.packageManager.resolveActivity(contract.createIntent(context, input),
        PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    val isStub = handler?.packageName == "com.google.android.tv.frameworkpackagestubs" &&
        handler.name == "com.google.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub"
    return !isStub && launchOrLogAbsence(input, what)
}
