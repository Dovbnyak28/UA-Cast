package com.uacastplayer.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.uacastplayer.core.concurrent.runCatchingNonFatal
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
        val candidate = apkSigningIdentity(context, file)
        val trusted = ApkTrustPolicy.isTrustedUpdate(
            installedCurrent = installed,
            candidateCurrent = candidate.current,
            candidateHistory = candidate.history,
        )
        if (!trusted) {
            // No certificate digests in the log. They are not secret, but a mismatch is the one
            // moment this app tells the user something is wrong, and hex nobody can act on is not
            // the thing to say. The counts distinguish "read nothing" from "read something else",
            // which is the only distinction worth having here.
            AppLog.w(TAG) {
                "Refusing an update signed by someone else: ${installed.size} installed signer(s), " +
                    "${candidate.current.size} in the file"
            }
        }
        return trusted
    }

    private fun installedSigners(context: Context): Set<String> = runCatchingNonFatal {
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
    private data class SigningIdentity(val current: Set<String>, val history: Set<String>)

    private fun apkSigningIdentity(context: Context, file: File): SigningIdentity = runCatchingNonFatal {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
        if (info == null) {
            AppLog.w(TAG) { "The downloaded file is not a readable APK" }
            return SigningIdentity(emptySet(), emptySet())
        }
        // Only the archive's *own* package name may be installed over this one. Android enforces
        // this too, but a mismatch here is a clearer thing to refuse early than a system dialog
        // offering to install some other app the user did not ask for.
        if (info.packageName != context.packageName) {
            AppLog.w(TAG) { "The downloaded APK is a different application" }
            return SigningIdentity(emptySet(), emptySet())
        }
        SigningIdentity(digestsOf(info), signingHistoryOf(info))
    }.getOrElse {
        AppLog.w(TAG) { "Cannot read the downloaded APK's signature: ${it.javaClass.simpleName}" }
        SigningIdentity(emptySet(), emptySet())
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

    /** On P+ this is verified by PackageManager from the APK's proof-of-rotation structure. On
     * older Android there is no key-rotation support, so the current signer is the whole history. */
    @Suppress("DEPRECATION")
    private fun signingHistoryOf(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        return signatures.orEmpty().filterNotNull().map { signature ->
            Hex.encode(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))
        }.toSet()
    }
}
