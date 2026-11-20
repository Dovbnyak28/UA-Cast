package com.uacastplayer.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable-emulator regression: never sleep or overwrite fixture state on a personal device. */
@RunWith(AndroidJUnit4::class)
class BenchmarkFixtureReadinessTest {
    @Test
    fun sleepingDisplayDoesNotHideFixtureReadiness() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1") {
            "Fixture readiness regression requires a disposable emulator"
        }
        try {
            device.sleep()
            assertFalse("Precondition: emulator display is off", device.isScreenOn)
            BenchmarkAppDriver(device).prepareFixture(BenchmarkAppDriver.MODE_PROFILE)
            assertTrue("Fixture setup must wake the display before reading UI status", device.isScreenOn)
        } finally {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.executeShellCommand("am force-stop ${BenchmarkAppDriver.PACKAGE_NAME}")
        }
    }
}
