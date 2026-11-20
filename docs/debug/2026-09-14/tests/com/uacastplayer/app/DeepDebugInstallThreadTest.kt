package com.uacastplayer.app

import android.os.Looper
import com.uacastplayer.data.update.InstallLaunch
import com.uacastplayer.data.update.UpdateDownload
import com.uacastplayer.update.ReleaseApk
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Opt-in thread-affinity test for the blocking APK installer boundary. */
@RunWith(RobolectricTestRunner::class)
class DeepDebugInstallThreadTest {
    @Test fun `blocking installer must not execute on UI thread`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            var installerRanOnMain: Boolean? = null
            val invoked = CountDownLatch(1)
            val controller = UpdateInstallController(
                scope = scope,
                download = { _, _ -> UpdateDownload.Ready(File("diagnostic-only.apk")) },
                install = {
                    installerRanOnMain = Looper.myLooper() == Looper.getMainLooper()
                    invoked.countDown()
                    InstallLaunch.Started(71)
                },
            )
            controller.downloadAndInstall(ReleaseApk("https://example.test/update.apk", 1024, null))
            assertTrue("The installer must have run", invoked.await(3, TimeUnit.SECONDS))
            assertEquals("APK parsing, staging and fsync must be off Main", false, installerRanOnMain)
        } finally {
            scope.cancel()
        }
    }
}
