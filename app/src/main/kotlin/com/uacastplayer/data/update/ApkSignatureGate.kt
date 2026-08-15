package com.uacastplayer.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.uacastplayer.core.security.Hex
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.ApkTrustPolicy
import java.io.File
import java.security.MessageDigest

private const val TAG = "ApkSignatureGate"

/**
 * Reads the signing certificates off a downloaded APK and off this installed app, and asks
 * [ApkTrustPolicy] whether they are the same.
 *
 * Everything here is the Android-specific half - `PackageManager` calls and a digest - kept thin so
 * the rule it feeds stays testable without a device. The rule is one line; the reason it matters is
 * in [ApkTrustPolicy]'s doc.
 *
 * A failure to read either side answers `false`. This is a gate, so "could not tell" and "no" have
 * to be the same answer; anything else would make an unreadable APK the easiest one to get past it.
 */
object ApkSignatureGate {

    /** True only when [file] was signed by whoever signed the running app. */
    fun isTrustedUpdate(context: Context, file: File): Boolean {
        val installed = installedSigners(context)
        val candidate = apkSigners(context, file)
        val trusted = ApkTrustPolicy.isSameSigner(installed, candidate)
        if (!trusted) {
            // No certificate digests in the log. They are not secret, but a mismatch is the one
            // moment this app tells the user something is wrong, and hex nobody can act on is not
            // the thing to say. The counts distinguish "read nothing" from "read something else",
            // which is the only distinction worth having here.
            AppLog.w(TAG) {
                "Refusing an update signed by someone else: ${installed.size} installed signer(s), " +
                    "${candidate.size} in the file"
            }
        }
        return trusted
    }

    private fun installedSigners(context: Context): Set<String> = runCatching {
        digestsOf(context.packageManager.getPackageInfo(context.packageName, signingFlags()))
    }.getOrElse {
        AppLog.w(TAG) { "Cannot read this app's own signature: ${it.javaClass.simpleName}" }
        emptySet()
    }

    /**
     * `getPackageArchiveInfo` parses the file without installing it, and returns null for anything
     * that is not a readable APK - a truncated download, an HTML error page saved under an .apk
     * name, a file the download never finished.
     */
    private fun apkSigners(context: Context, file: File): Set<String> = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
        if (info == null) {
            AppLog.w(TAG) { "The downloaded file is not a readable APK" }
            return emptySet()
        }
        // Only the archive's *own* package name may be installed over this one. Android enforces
        // this too, but a mismatch here is a clearer thing to refuse early than a system dialog
        // offering to install some other app the user did not ask for.
        if (info.packageName != context.packageName) {
            AppLog.w(TAG) { "The downloaded APK is a different application" }
            return emptySet()
        }
        digestsOf(info)
    }.getOrElse {
        AppLog.w(TAG) { "Cannot read the downloaded APK's signature: ${it.javaClass.simpleName}" }
        emptySet()
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    /**
     * The current signers, on either API. Below P there is only `signatures`; from P
     * `apkContentsSigners` is the equivalent, and for a rotated key it reports the current signer
     * rather than the whole lineage - which is exactly what [ApkTrustPolicy] is written against.
     */
    @Suppress("DEPRECATION")
    private fun digestsOf(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().filterNotNull().map { signature ->
            Hex.encode(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))
        }.toSet()
    }
}
