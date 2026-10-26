package com.uacastplayer.update

/**
 * How far the "download and install" action has got.
 *
 * Deliberately not folded into [UpdateCheckOutcome]. That one answers a question the user asked
 * ("is there an update?") and is cleared once read; this one describes work in progress that must
 * survive being looked at, and its failures are ones the user can act on differently -
 * [NeedsPermission] is a Settings screen away, [Untrusted] is not retryable at all, and [Failed] is
 * "try again".
 */
sealed interface UpdateInstallState {

    /** Nothing has been asked for, or the last attempt has been read and cleared. */
    data object Idle : UpdateInstallState

    /**
     * @param totalBytes what the release declared, or 0 when it declared nothing - which the UI
     *   shows as an indeterminate bar rather than as a percentage of zero.
     */
    data class Downloading(val bytesSoFar: Long, val totalBytes: Long) : UpdateInstallState

    /** Verified and handed to the system package installer. Whatever happens next is the system's
     * to report, and it may well be that this app is replaced mid-sentence. */
    data object Launching : UpdateInstallState

    /** The user has not allowed this app to install packages. One Settings screen away, and the
     * only failure here with a button worth offering. */
    data object NeedsPermission : UpdateInstallState

    /**
     * The downloaded APK was not signed by whoever signed the running app, or is a different app
     * altogether. Not a transport failure and not worth retrying: the same file would be fetched
     * and refused again. See [ApkTrustPolicy] for why this is refused here rather than left to the
     * system to refuse later.
     */
    data object Untrusted : UpdateInstallState

    /** The bytes are not the bytes the release described - wrong length, or a hash that does not
     * match. Retryable, since a truncated download often is. */
    data object Corrupt : UpdateInstallState

    /** Offline, a timeout, a full disk, a package installer that refused the session. */
    data object Failed : UpdateInstallState
}
