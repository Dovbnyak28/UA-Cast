package com.uacastplayer.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.uacastplayer.log.AppLog
import java.io.File
import java.io.IOException

private const val TAG = "ApkInstaller"

/** What happened when an install was asked for. Nothing here means the app was updated - that is
 * the system's answer, and it arrives later at [UpdateInstallReceiver]. */
sealed interface InstallLaunch {

    /** Handed to the system package installer. Whether it then asks the user is the platform's
     * decision - see [ApkInstaller.install]. */
    data object Started : InstallLaunch

    /** The user has not allowed this app to install packages. Recoverable, and the UI's job: send
     * them to the "install unknown apps" screen for this app. */
    data object NeedsPermission : InstallLaunch

    /** The file was not signed by whoever signed the running app, or is not this app at all. Not
     * recoverable and not retryable - see [ApkSignatureGate]. */
    data object Untrusted : InstallLaunch

    /** A full disk, a session the system refused to open, an unreadable file. */
    data object Failed : InstallLaunch
}

/**
 * Hands a downloaded APK to the system package installer.
 *
 * Uses [PackageInstaller] rather than an `ACTION_VIEW` intent on a `content://` URI. Two reasons,
 * and the second is the one that matters: it reports its result back through
 * [UpdateInstallReceiver] instead of ending in a screen the app never hears about again, and it
 * needs no `FileProvider` grant, so the update file stays inside the app's own cache rather than
 * being exported to whatever resolves the intent.
 *
 * **Whether the user sees a confirmation dialog is the platform's decision, not this app's.** From
 * API 31 a session may ask not to require user action, which is what
 * `UPDATE_PACKAGES_WITHOUT_USER_ACTION` in the manifest is for, and the system grants that only
 * under its own conditions - which include this app being the installer of record for the package
 * it is updating. The very first update installed through here cannot satisfy that, because
 * something else installed the app originally. So the dialog is expected at least once, possibly
 * always, and [UpdateInstallReceiver] handles [PackageInstaller.STATUS_PENDING_USER_ACTION] as an
 * ordinary outcome rather than a failure. Asking costs one line and changes nothing when refused.
 *
 * The signature gate runs first, before a session is even opened. Android would refuse a mismatched
 * signature too, but only after the file has been handed over, inside a system dialog, with a
 * message the user can do nothing with.
 */
object ApkInstaller {

    private const val SESSION_ENTRY = "uacast-update.apk"
    private const val REQUEST_CODE = 0x0DA7E

    fun install(context: Context, file: File): InstallLaunch = when {
        // Signature first, before the permission: an APK this app will refuse anyway is refused
        // without sending the user off to a Settings screen for nothing.
        !ApkSignatureGate.isTrustedUpdate(context, file) -> InstallLaunch.Untrusted
        !canInstallPackages(context) -> InstallLaunch.NeedsPermission
        else -> commitSession(context, file)
    }

    /** Whether the user has allowed this app to install packages. Below API 26 there is no such
     * per-app switch: the manifest permission is the whole of it. */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    @Suppress("TooGenericExceptionCaught")
    private fun commitSession(context: Context, file: File): InstallLaunch {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.stage(file)
                session.commit(statusIntent(context, sessionId).intentSender)
            }
            AppLog.d(TAG) { "Update session $sessionId committed" }
            InstallLaunch.Started
        } catch (e: IOException) {
            AppLog.w(TAG) { "Could not stage the update: ${e.javaClass.simpleName}" }
            abandon(installer, sessionId)
            InstallLaunch.Failed
        } catch (e: Exception) {
            // createSession/commit throw SecurityException and IllegalArgumentException of their
            // own accord - a device whose package installer refuses this app is a failed update,
            // never a crash on a button the user pressed.
            AppLog.w(TAG) { "The package installer refused the session: ${e.javaClass.simpleName}" }
            abandon(installer, sessionId)
            InstallLaunch.Failed
        }
    }

    /** Copies the APK into the session. `fsync` before the stream closes is what the package
     * installer documents as required: without it the staged copy can be short of the bytes the
     * session was told to expect, and commit fails on a file that is perfectly good on disk. */
    private fun PackageInstaller.Session.stage(file: File) {
        openWrite(SESSION_ENTRY, 0, file.length()).use { out ->
            file.inputStream().use { it.copyTo(out) }
            fsync(out)
        }
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        if (sessionId < 0) return
        // A session left open holds the staged copy of the APK on disk until the system decides to
        // reclaim it, which is the same wasted tens of megabytes the download sweep exists for.
        runCatching { installer.abandonSession(sessionId) }
    }

    /**
     * Mutable on purpose, and it has to be: the system fills this intent in with the session's
     * status and, when it wants the user to confirm, with the confirmation intent itself. An
     * immutable one would arrive empty and [UpdateInstallReceiver] would have nothing to act on.
     */
    private fun statusIntent(context: Context, sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
