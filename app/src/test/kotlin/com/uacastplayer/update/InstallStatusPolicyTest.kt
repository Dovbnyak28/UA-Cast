package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a committed install session's status means to the user interface.
 *
 * The values are pinned against the platform's own numbers rather than only against each other,
 * because they arrive from `PackageInstaller.EXTRA_STATUS` and a mapping that agreed with itself
 * while disagreeing with Android would be silently wrong in exactly the direction that matters.
 */
class InstallStatusPolicyTest {

    @Test
    fun theThreeNumbersMatchThePlatformsOwn() {
        assertEquals(android.content.pm.PackageInstaller.STATUS_SUCCESS, InstallStatusPolicy.STATUS_SUCCESS)
        assertEquals(android.content.pm.PackageInstaller.STATUS_FAILURE, InstallStatusPolicy.STATUS_FAILURE)
        assertEquals(
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION,
            InstallStatusPolicy.STATUS_PENDING_USER_ACTION,
        )
    }

    @Test
    fun aSuccessfulInstallIsRecognised() {
        assertEquals(
            InstallSessionOutcome.Installed,
            InstallStatusPolicy.outcomeFor(InstallStatusPolicy.STATUS_SUCCESS),
        )
    }

    /** Not a failure: the session is alive and the system is showing its own dialog. */
    @Test
    fun beingAskedToConfirmIsNotAnEnding() {
        assertEquals(
            InstallSessionOutcome.AwaitingUser,
            InstallStatusPolicy.outcomeFor(InstallStatusPolicy.STATUS_PENDING_USER_ACTION),
        )
    }

    @Test
    fun pendingWithoutLaunchableConfirmationIsAFailure() {
        assertEquals(
            InstallSessionOutcome.Failed,
            InstallStatusPolicy.outcomeFor(
                InstallStatusPolicy.STATUS_PENDING_USER_ACTION,
                userActionLaunched = false,
            ),
        )
    }

    /**
     * Every documented failure, as one outcome.
     *
     * The list is written out rather than summarised, because "everything else fails" is easy to
     * write and easy to get wrong by accident - a `when` that quietly folded one of these into the
     * success branch would read fine.
     *
     * `STATUS_FAILURE_ABORTED` (3) is the one this was found through: it is what Google Play
     * Protect's refusal arrives as, and on a phone with Play services that is the *ordinary* first
     * outcome for an APK installed from outside the store.
     */
    @Test
    fun everyWayAnInstallCanEndBadlyIsOneOutcome() {
        val failures = listOf(
            android.content.pm.PackageInstaller.STATUS_FAILURE,
            android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED,
            android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED,
            android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID,
            android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT,
            android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE,
            android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        )

        failures.forEach {
            assertEquals(
                "status $it must not be read as anything but a failure",
                InstallSessionOutcome.Failed,
                InstallStatusPolicy.outcomeFor(it),
            )
        }
    }

    /**
     * A status this app has never heard of cannot be claimed to have ended well.
     *
     * The cost of being wrong runs one way only: calling a real failure "installed" leaves the user
     * with no way forward, while calling a real success "failed" is corrected the moment the app
     * relaunches as the new version.
     */
    @Test
    fun anUnknownStatusIsTreatedAsAFailure() {
        assertEquals(InstallSessionOutcome.Failed, InstallStatusPolicy.outcomeFor(UNKNOWN_FUTURE_STATUS))
        assertEquals(InstallSessionOutcome.Failed, InstallStatusPolicy.outcomeFor(Int.MIN_VALUE))
    }

    private companion object {
        /** Past everything the platform defines today - a vendor addition, or a future API level. */
        const val UNKNOWN_FUTURE_STATUS = 99
    }
}
