package com.uacastplayer.app

import com.uacastplayer.data.update.InstallLaunch
import com.uacastplayer.data.update.UpdateDownload
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private fun controller(
        result: UpdateDownload = UpdateDownload.Ready(file),
        launch: InstallLaunch = InstallLaunch.Started,
    ) = UpdateInstallController(
        scope = scope,
        download = { _, _ -> downloadCalls++; result },
        install = { installCalls++; launch },
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
            install = { InstallLaunch.Started },
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
            install = { InstallLaunch.Started },
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
            install = { InstallLaunch.Started },
        )
        controller.downloadAndInstall(apk)

        controller.clearOutcome()

        assertEquals(UpdateInstallState.Downloading(100L, 1000L), controller.state.value)
        held.complete(UpdateDownload.Ready(file))
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
            install = { InstallLaunch.Started },
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
}
