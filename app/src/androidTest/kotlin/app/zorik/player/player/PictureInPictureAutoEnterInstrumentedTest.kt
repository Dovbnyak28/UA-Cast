package app.zorik.player.player

import android.os.Build
import android.os.SystemClock
import androidx.media3.common.VideoSize
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.zorik.player.MainActivity
import app.zorik.player.ui.player.PipController
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home during fullscreen playback must enter picture-in-picture on every release that has it.
 *
 * From API 31 the system does it from the params [PipController.syncParams] keeps current. API
 * 26-30 have no `setAutoEnterEnabled`, so `MainActivity.onUserLeaveHint` enters from the same
 * recorded request - before that fallback, Home on a Mi A2 (Android 11) just paused the video. The
 * request is set directly here, as the player does while fullscreen video is playing, so the test
 * covers the platform path without depending on a stream.
 */
@RunWith(AndroidJUnit4::class)
class PictureInPictureAutoEnterInstrumentedTest {

    @Test fun homeWithAPlayingFullscreenRequestEntersPictureInPicture() {
        assumeTrue("No picture-in-picture before API 26", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity {
                activity = it
                PipController.syncParams(it, VideoSize(1280, 720), null, autoEnter = true)
            }
            device.pressHome()
            val entered = waitUntil(PIP_TIMEOUT_MILLIS) {
                var inPip = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    inPip = activity?.isInPictureInPictureMode == true
                }
                inPip
            }
            scenario.onActivity { PipController.disableAutoEnter(it) }
            assertTrue("Home did not enter picture-in-picture on API ${Build.VERSION.SDK_INT}", entered)
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(POLL_MILLIS)
        }
        return condition()
    }

    private companion object {
        const val PIP_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 200L
    }
}
