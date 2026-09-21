package com.uacastplayer.app

import com.uacastplayer.data.update.InstallLaunch
import com.uacastplayer.data.update.UpdateDownload
import com.uacastplayer.update.InstallSessionOutcome
import com.uacastplayer.update.InstallSessionResult
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state a user watches while an update installs.
 *
 * The downloader and installer are functions here rather than objects, which is exactly why they are
 * functions in production too: underneath they are a socket, a `PackageInstaller` session and a
 * `PackageManager`, none of which this file needs an opinion about. What it does need an opinion
 * about is which of their answers the user is shown, and that each one leaves them something to do.
 */
class UpdateInstallControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private val apk = ReleaseApk(downloadUrl = "https://example.test/u.apk", sizeBytes = 1000, sha256 = null)
    private val file = File("downloaded.apk")

    private var downloadCalls = 0
    private var installCalls = 0

    /** What [com.uacastplayer.data.update.UpdateInstallReceiver] publishes in production, so a test
     * can be the system reporting back without a device or a broadcast. */
    private val outcomes = MutableSharedFlow<InstallSessionResult>(extraBufferCapacity = 1)

    private fun controller(
        result: UpdateDownload = UpdateDownload.Ready(file),
        launch: InstallLaunch = InstallLaunch.Started(SESSION_ID),
    ) = UpdateInstallController(
        scope = scope,
        download = { _, _ -> downloadCalls++; result },
        install = { installCalls++; launch },
        outcomes = outcomes,
    )

    @Test
    fun `a verified download is handed straight to the installer`() {
        val controller = controller()

        controller.downloadAndInstall(apk)

        assertEquals(UpdateInstallState.Launching, controller.state.value)
        assertEquals(1, installCalls)
    }

    @Test
    fun `the state moves through downloading before it reaches the installer`() {
        val held = CompletableDeferred<UpdateDownload>()
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, onProgress ->
                onProgress(400L, 1000L)
                held.await()
            },
            install = { InstallLaunch.Started(SESSION_ID) },
        )

        controller.downloadAndInstall(apk)

        assertEquals(UpdateInstallState.Downloading(400L, 1000L), controller.state.value)
        held.complete(UpdateDownload.Ready(file))
        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    /**
     * A second press must not restart a download the user is watching, nor queue a second copy of
     * the same tens of megabytes. Asserted by counting, since both mistakes look identical from the
     * state alone.
     */
    @Test
    fun `pressing again while a download runs is ignored`() {
        val held = CompletableDeferred<UpdateDownload>()
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, _ -> downloadCalls++; held.await() },
            install = { InstallLaunch.Started(SESSION_ID) },
        )

        controller.downloadAndInstall(apk)
        controller.downloadAndInstall(apk)
        controller.downloadAndInstall(apk)

        assertEquals(1, downloadCalls)
        held.complete(UpdateDownload.Ready(file))
    }

    /** The control: once it has finished, the button works again - which is what makes a failed
     * attempt retryable. */
    @Test
    fun `pressing again after one finishes starts a new download`() {
        val controller = controller(result = UpdateDownload.Failed)

        controller.downloadAndInstall(apk)
        controller.downloadAndInstall(apk)

        assertEquals(2, downloadCalls)
    }

    @Test
    fun `each download failure is reported as the thing the user can act on`() {
        assertEquals(
            "a damaged download is worth repeating, so it says so",
            UpdateInstallState.Corrupt,
            controller(result = UpdateDownload.Corrupt).also { it.downloadAndInstall(apk) }.state.value,
        )
        assertEquals(
            UpdateInstallState.Failed,
            controller(result = UpdateDownload.Failed).also { it.downloadAndInstall(apk) }.state.value,
        )
        assertEquals(
            "nothing the user can do differently about an oversized file, so it reads as a failure",
            UpdateInstallState.Failed,
            controller(result = UpdateDownload.TooLarge).also { it.downloadAndInstall(apk) }.state.value,
        )
    }

    @Test
    fun `each install refusal is reported as the thing the user can act on`() {
        assertEquals(
            UpdateInstallState.NeedsPermission,
            controller(launch = InstallLaunch.NeedsPermission).also { it.downloadAndInstall(apk) }.state.value,
        )
        assertEquals(
            UpdateInstallState.Untrusted,
            controller(launch = InstallLaunch.Untrusted).also { it.downloadAndInstall(apk) }.state.value,
        )
        assertEquals(
            UpdateInstallState.Failed,
            controller(launch = InstallLaunch.Failed).also { it.downloadAndInstall(apk) }.state.value,
        )
    }

    /** A file that failed its download never reaches the installer - there is nothing to install. */
    @Test
    fun `a failed download never reaches the installer`() {
        controller(result = UpdateDownload.Corrupt).downloadAndInstall(apk)

        assertEquals(0, installCalls)
    }

    @Test
    fun `an unexpected downloader exception becomes a retryable failure`() {
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, _ -> throw IllegalStateException("provider failed") },
            install = { installCalls++; InstallLaunch.Started(SESSION_ID) },
            outcomes = outcomes,
        )

        controller.downloadAndInstall(apk)

        assertEquals(UpdateInstallState.Failed, controller.state.value)
        assertEquals(0, installCalls)
    }

    @Test
    fun `an unexpected installer exception becomes a retryable failure`() {
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, _ -> UpdateDownload.Ready(file) },
            install = { throw SecurityException("installer unavailable") },
            outcomes = outcomes,
        )

        controller.downloadAndInstall(apk)

        assertEquals(UpdateInstallState.Failed, controller.state.value)
    }

    @Test
    fun `a read outcome is cleared so the row goes back to offering the install`() {
        val controller = controller(launch = InstallLaunch.NeedsPermission)
        controller.downloadAndInstall(apk)

        controller.clearOutcome()

        assertEquals(UpdateInstallState.Idle, controller.state.value)
    }

    /**
     * Leaving Settings clears the row, and that must not wipe a download in flight - a user who
     * starts 40MB and goes to watch something should come back to a finished one, not to nothing.
     */
    @Test
    fun `clearing does not touch a download that is still running`() {
        val held = CompletableDeferred<UpdateDownload>()
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, onProgress -> onProgress(100L, 1000L); held.await() },
            install = { InstallLaunch.Started(SESSION_ID) },
        )
        controller.downloadAndInstall(apk)

        controller.clearOutcome()

        assertEquals(UpdateInstallState.Downloading(100L, 1000L), controller.state.value)
        held.complete(UpdateDownload.Ready(file))
        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    @Test
    fun `leaving settings cannot hide an active installer session`() {
        val controller = controller()
        controller.downloadAndInstall(apk)

        controller.clearOutcome()

        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    @Test
    fun `repeated install request is ignored while package installer owns active session`() {
        val controller = controller()
        controller.downloadAndInstall(apk)

        controller.downloadAndInstall(apk)

        assertEquals(1, downloadCalls)
        assertEquals(1, installCalls)
        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    /**
     * The last chunk's progress callback can land after the download has already returned. Without
     * the guard it would drag a finished install back to "downloading" and leave it there.
     */
    @Test
    fun `a progress callback arriving after the result cannot undo it`() {
        var late: ((Long, Long) -> Unit)? = null
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, onProgress -> late = onProgress; UpdateDownload.Ready(file) },
            install = { InstallLaunch.Started(SESSION_ID) },
        )
        controller.downloadAndInstall(apk)
        assertEquals(UpdateInstallState.Launching, controller.state.value)

        late?.invoke(999L, 1000L)

        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    @Test
    fun `nothing has happened before the button is pressed`() {
        val controller = controller()

        assertEquals(UpdateInstallState.Idle, controller.state.value)
        assertTrue(downloadCalls == 0 && installCalls == 0)
    }

    /**
     * The dead end this exists to close.
     *
     * A committed session is not the end of the story. `Launching` says "confirm the install on
     * screen", and until the system's verdict came back here that message stayed up for good
     * whatever happened next - no button to retry, nothing to press, only a restart cleared it.
     *
     * **Measured on a Mi A2 against the real v0.9.1 release**: Google Play Protect refuses a
     * sideloaded APK by default (`VerifyApps: Returning package verification result, result=REJECT`)
     * and the install simply does not happen. So the ordinary first attempt at updating an app
     * published outside Play ended exactly here.
     */
    @Test
    fun `a session the system refused stops telling the user to confirm it`() {
        val controller = controller()
        controller.downloadAndInstall(apk)
        assertEquals(UpdateInstallState.Launching, controller.state.value)

        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.Failed))

        assertEquals(UpdateInstallState.Failed, controller.state.value)
    }

    /** Being asked to confirm is not an ending - the session is alive and the system is showing its
     * own dialog, which is what `Launching` already says. Moving off it here would replace a true
     * message with a false one. */
    @Test
    fun `waiting on the user's confirmation changes nothing`() {
        val controller = controller()
        controller.downloadAndInstall(apk)

        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.AwaitingUser))

        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    /** A success needs nothing done: this process is about to be replaced by the version it just
     * installed, and there is no screen left to correct. */
    @Test
    fun `a successful install is left alone`() {
        val controller = controller()
        controller.downloadAndInstall(apk)

        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.Installed))

        assertEquals(UpdateInstallState.Launching, controller.state.value)
    }

    /**
     * The guard that makes the collector safe to leave running for the controller's whole life.
     *
     * A verdict from a session the user has moved on from - they dismissed the dialog, pressed the
     * button again, and are now watching a fresh download - must not drag the state backwards into
     * a failure that is no longer true.
     */
    @Test
    fun `a late verdict cannot disturb a download that has since started`() {
        val held = CompletableDeferred<UpdateDownload>()
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, onProgress -> onProgress(10L, 1000L); held.await() },
            install = { InstallLaunch.Started(SESSION_ID) },
            outcomes = outcomes,
        )
        controller.downloadAndInstall(apk)
        assertEquals(UpdateInstallState.Downloading(10L, 1000L), controller.state.value)

        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.Failed))

        assertEquals(UpdateInstallState.Downloading(10L, 1000L), controller.state.value)
        held.complete(UpdateDownload.Ready(file))
    }

    @Test
    fun `a late failure from an older launched session cannot fail the current launched session`() {
        var nextSessionId = SESSION_ID
        val controller = UpdateInstallController(
            scope = scope,
            download = { _, _ -> UpdateDownload.Ready(file) },
            install = { InstallLaunch.Started(nextSessionId++) },
            outcomes = outcomes,
        )

        controller.downloadAndInstall(apk)
        assertEquals(UpdateInstallState.Launching, controller.state.value)
        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.Failed))
        assertEquals(UpdateInstallState.Failed, controller.state.value)
        controller.downloadAndInstall(apk)
        assertEquals(UpdateInstallState.Launching, controller.state.value)

        // A duplicate broadcast from the previous session can arrive after the retry was launched.
        outcomes.tryEmit(InstallSessionResult(SESSION_ID, InstallSessionOutcome.Failed))

        assertEquals(
            "session $SESSION_ID is stale; session ${SESSION_ID + 1} is still waiting on the installer",
            UpdateInstallState.Launching,
            controller.state.value,
        )
    }

    private companion object {
        const val SESSION_ID = 41
    }
}
