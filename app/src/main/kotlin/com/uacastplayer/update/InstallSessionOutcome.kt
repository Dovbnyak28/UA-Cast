package com.uacastplayer.update

/**
 * What the system finally did with an install session, reduced to the three answers the UI needs.
 *
 * A committed session is not the end of the story, and treating it as one is what this exists to
 * correct. [UpdateInstallState.Launching] used to be terminal in the interface: the app said
 * "confirm the install on screen" and stayed there for good, whatever happened next. Every ending
 * except a successful install left the user looking at an instruction about a dialog that was no
 * longer there, with no button to try again - only a restart cleared it.
 *
 * That is not an edge case. **Measured on a Mi A2 against the real v0.9.1 release**: Google Play
 * Protect refuses a sideloaded APK by default - `VerifyApps: Returning package verification result,
 * result=REJECT` - and the install never happens. So the ordinary first attempt at updating an app
 * published outside Play ends here, and it ended in a dead end.
 */
enum class InstallSessionOutcome {

    /** The app is being replaced by the version it just installed. Nothing to show - see
     * [InstallStatusPolicy.outcomeFor]. */
    Installed,

    /** The system wants the user to confirm, and has handed over the intent that asks. Not a
     * failure: the session is alive and waiting. */
    AwaitingUser,

    /**
     * The session ended without installing: refused by Play Protect, cancelled at the dialog, a
     * downgrade, an incompatible update, a full disk. One answer, because the user's move is the
     * same for all of them - try again, or fetch it from the release page.
     */
    Failed,
}

/** A PackageInstaller verdict names the session it belongs to. Without this identity, a delayed
 * failure from an abandoned session can overwrite a newer session that has already launched. */
data class InstallSessionResult(val sessionId: Int, val outcome: InstallSessionOutcome)

/**
 * Reads a `PackageInstaller.EXTRA_STATUS` value.
 *
 * A plain `Int` rather than the platform constants, so the mapping can be tested without a device.
 * The three values named below are the whole of what this app distinguishes; the rest -
 * `STATUS_FAILURE_BLOCKED`, `_ABORTED`, `_INVALID`, `_CONFLICT`, `_STORAGE`, `_INCOMPATIBLE`, and
 * anything a vendor adds later - are one outcome on purpose. Telling a user which of six ways an
 * install failed does not change what they can do about it, and the system's own message (kept in
 * the log by [com.uacastplayer.data.update.UpdateInstallReceiver]) is the only place the real
 * reason is ever stated in words.
 */
object InstallStatusPolicy {

    /** `PackageInstaller.STATUS_SUCCESS`. */
    const val STATUS_SUCCESS = 0

    /** `PackageInstaller.STATUS_FAILURE`. */
    const val STATUS_FAILURE = 1

    /** `PackageInstaller.STATUS_PENDING_USER_ACTION`. */
    const val STATUS_PENDING_USER_ACTION = -1

    /**
     * Anything unrecognised is a failure, deliberately. A status this app has never heard of is one
     * it cannot claim ended well, and the cost of being wrong runs one way only: calling a real
     * failure "installed" leaves the user with no way forward, while calling a real success
     * "failed" is corrected the moment the app relaunches as the new version.
     */
    fun outcomeFor(status: Int, userActionLaunched: Boolean = true): InstallSessionOutcome = when (status) {
        STATUS_SUCCESS -> InstallSessionOutcome.Installed
        STATUS_PENDING_USER_ACTION -> if (userActionLaunched) {
            InstallSessionOutcome.AwaitingUser
        } else {
            // A pending session without a launchable confirmation intent cannot make progress.
            // Calling it AwaitingUser leaves the UI on an instruction with no dialog behind it.
            InstallSessionOutcome.Failed
        }
        else -> InstallSessionOutcome.Failed
    }
}
