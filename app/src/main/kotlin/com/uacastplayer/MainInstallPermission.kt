package com.uacastplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.uacastplayer.data.update.ApkInstaller
import com.uacastplayer.log.AppLog

/**
 * Opens the system screen where this app can be allowed to install packages.
 *
 * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is a documented action, not a guaranteed one - the same
 * assumption `BatteryOptimizationDialog` was crashing on before it was fixed, and for the same
 * reason: a stripped or replaced Settings app resolves nothing and `startActivity` on an
 * unresolvable intent throws `ActivityNotFoundException`, which is unchecked. Here it would land on
 * a user who has already waited out a download, so it degrades to a log line and a row that keeps
 * saying permission is needed.
 *
 * `package:` on the intent's data is what makes the system open this app's own switch rather than
 * the list of every app; without it the user has to find UA Cast in a list themselves.
 *
 * Below API 26 there is no per-app switch at all - the manifest permission is the whole of it - so
 * nothing here is ever reached on those devices (see [ApkInstaller.canInstallPackages]).
 */
internal fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        .setData("package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        AppLog.w("MainActivity") { "this device has no install-permission screen: ${e.javaClass.simpleName}" }
    }
}

