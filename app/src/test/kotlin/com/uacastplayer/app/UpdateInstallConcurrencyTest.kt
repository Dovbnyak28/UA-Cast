package com.uacastplayer.app

import android.os.Looper
import com.uacastplayer.data.update.InstallLaunch
import com.uacastplayer.data.update.UpdateDownload
import com.uacastplayer.update.InstallSessionOutcome
import com.uacastplayer.update.InstallSessionResult
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UpdateInstallConcurrencyTest {
    private val apk = ReleaseApk("https://example.test/update.apk", 1024, null)
    private val file = File("fixture.apk")

    @Test fun `installer runs off Main and UI remains responsive during staging`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var onMain: Boolean? = null
        try {
            val controller = UpdateInstallController(scope, { _, _ -> UpdateDownload.Ready(file) }, {
                onMain = Looper.myLooper() == Looper.getMainLooper()
                entered.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                    InstallLaunch.Started(71)
                } finally { finished.countDown() }
            })
            controller.downloadAndInstall(apk)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(false, onMain)
            var uiTick = false
            android.os.Handler(Looper.getMainLooper()).post { uiTick = true }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(uiTick)
            controller.clearOutcome()
            controller.downloadAndInstall(apk)
            assertTrue(controller.state.value is UpdateInstallState.Downloading)
        } finally {
            release.countDown()
            finished.await(5, TimeUnit.SECONDS)
            scope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun `early failure for committed session survives IO result handoff`() {
        checkEarlyFailure(failedId = 71, expected = UpdateInstallState.Failed)
    }

    @Test fun `early failure for unrelated session cannot poison current install`() {
        checkEarlyFailure(failedId = 70, expected = UpdateInstallState.Launching)
    }

    private fun checkEarlyFailure(failedId: Int, expected: UpdateInstallState) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val outcomes = MutableSharedFlow<InstallSessionResult>()
        val committed = CompletableDeferred<InstallLaunch>()
        var calls = 0
        try {
            val controller = UpdateInstallController(scope, { _, _ -> UpdateDownload.Ready(file) }, {
                calls++
                committed.await()
            }, outcomes, Dispatchers.Unconfined)
            controller.downloadAndInstall(apk)
            kotlinx.coroutines.runBlocking {
                outcomes.emit(InstallSessionResult(failedId, InstallSessionOutcome.Failed))
            }
            controller.downloadAndInstall(apk)
            assertEquals(1, calls)
            committed.complete(InstallLaunch.Started(71))
            assertEquals(expected, controller.state.value)
        } finally { scope.cancel() }
    }
}
