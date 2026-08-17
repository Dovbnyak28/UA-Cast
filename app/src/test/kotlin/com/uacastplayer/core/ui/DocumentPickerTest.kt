package com.uacastplayer.core.ui

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens when the tap lands on a device with no document picker.
 *
 * The launcher is a fake rather than a real one, because the failure being guarded is the framework
 * saying "nothing here can do that" - which is a property of the device, not of this code, and the
 * only devices that say it are ones no test runner runs on. What is worth pinning is that the
 * exception it raises does not leave the tap handler, since it is unchecked and would otherwise
 * arrive on the main thread as a crash.
 */
class DocumentPickerTest {

    private class FakeLauncher(private val throwOnLaunch: Boolean) : ActivityResultLauncher<Array<String>>() {
        var launchedWith: Array<String>? = null

        override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
            if (throwOnLaunch) throw ActivityNotFoundException("no activity handles ACTION_OPEN_DOCUMENT")
            launchedWith = input
        }

        override fun unregister() = Unit

        override val contract: ActivityResultContract<Array<String>, *> = ActivityResultContracts.OpenDocument()
    }

    @Test
    fun `a device with no picker gets a log line rather than a crash`() {
        val launcher = FakeLauncher(throwOnLaunch = true)

        launcher.launchOrLogAbsence(arrayOf("application/json"), "import a backup")
    }

    /** The control: where a picker does exist, the request still goes through it unchanged. */
    @Test
    fun `a device with a picker is asked for exactly what was requested`() {
        val launcher = FakeLauncher(throwOnLaunch = false)

        launcher.launchOrLogAbsence(arrayOf("audio/x-mpegurl", "*/*"), "pick a playlist")

        val requested = launcher.launchedWith
        assertTrue("the launcher was never asked for anything", requested != null)
        assertEquals(listOf("audio/x-mpegurl", "*/*"), requested?.toList())
    }
}
