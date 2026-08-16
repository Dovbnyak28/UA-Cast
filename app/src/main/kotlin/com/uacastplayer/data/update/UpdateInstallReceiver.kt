package com.uacastplayer.data.update

import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.InstallStatusPolicy

private const val TAG = "UpdateInstallReceiver"

/**
 * Where the system reports what became of an install session.
 *
 * The case that does real work is [PackageInstaller.STATUS_PENDING_USER_ACTION]. It is not an
 * error: it is the system saying "ask the user first", and it hands over the very intent that does
 * the asking. Ignoring it would leave a committed session sitting there forever with the update
 * downloaded, verified, staged - and nothing on screen. See [ApkInstaller] for why that arrives at
 * least once even though the session asks not to require it.
 *
 * `FLAG_ACTIVITY_NEW_TASK` because a broadcast receiver has no task of its own to start an activity
 * in. And wrapped in a catch for [ActivityNotFoundException], for the reason
 * `BatteryOptimizationDialogTest` records about a device whose Settings does not export what the
 * app asks for: a hint that cannot be shown is a hint not shown, never a crash - and here the crash
 * would land on a user who has already waited out a download.
 *
 * A success needs nothing done. The app is being replaced by the version it just installed, so
 * there is no state left worth updating and no screen left to update it on.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        // Reported before anything else here, and unconditionally. Whoever is showing "confirm the
        // install on screen" has no other way to learn that the screen in question is gone: the
        // session was committed, and every ending except a success used to arrive here, get logged,
        // and stop - leaving that message up for good with nothing to press. See
        // [com.uacastplayer.update.InstallSessionOutcome] for the field measurement behind this.
        InstallOutcomeBus.report(InstallStatusPolicy.outcomeFor(status))
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> askTheUser(context, intent)
            PackageInstaller.STATUS_SUCCESS -> AppLog.d(TAG) { "Update installed" }
            else -> AppLog.w(TAG) {
                // The message is the system's own and says things like "INSTALL_FAILED_VERSION_
                // DOWNGRADE" or "INSTALL_FAILED_UPDATE_INCOMPATIBLE" - worth keeping verbatim,
                // since it is the only place the real reason is ever stated.
                "Update not installed (status $status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
            }
        }
    }

    private fun askTheUser(context: Context, intent: Intent) {
        val confirmation = confirmationIntent(intent)
        if (confirmation == null) {
            AppLog.w(TAG) { "The installer asked for confirmation but sent no way to ask for it" }
            return
        }
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(confirmation)
        } catch (e: ActivityNotFoundException) {
            AppLog.w(TAG) { "Nothing on this device can show the install confirmation: ${e.javaClass.simpleName}" }
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.uacastplayer.action.INSTALL_STATUS"
    }
}
