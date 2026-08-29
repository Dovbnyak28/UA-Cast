package com.uacastplayer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Stable English selectors and fixture setup shared by profile generation and macrobenchmarks. */
internal class BenchmarkAppDriver(private val device: UiDevice) {

    /**
     * Prepare the synthetic state without destroying a user's production data by default.
     *
     * The target package is the real `com.uacastplayer` application, not a benchmark-only clone.
     * A blanket `pm clear` used to be the default here, which made running a macrobenchmark on a
     * connected phone silently delete its playlists, favourites and settings. The benchmark
     * fixture overwrites the active source and the files it owns, so a full clear is unnecessary
     * for normal runs. Keep the escape hatch for a deliberately dedicated device, but make the
     * destructive choice visible at every call site.
     */
    fun prepareFixture(mode: String, clearPackage: Boolean = false) {
        if (clearPackage) {
            val clearResult = device.executeShellCommand("pm clear $PACKAGE_NAME")
            check(clearResult.contains("Success")) { "Could not clear benchmark package" }
        } else {
            device.executeShellCommand("am force-stop $PACKAGE_NAME")
        }
        device.executeShellCommand(
            "am start -W -n $FIXTURE_COMPONENT --es $FIXTURE_MODE_EXTRA $mode",
        )
        val status = waitForStatus(FIXTURE_STATUS_PATTERN, FIXTURE_TIMEOUT_MILLIS)
        check(status == FIXTURE_READY) { status }
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
    }

    fun startMain(scope: MacrobenchmarkScope) {
        scope.startActivityAndWait()
        waitForText(HOME_LABEL)
    }

    fun openChannels() {
        clickDescription(CHANNELS_LABEL)
        waitForText(FIRST_GROUP)
    }

    fun openFirstGroup() {
        clickText(FIRST_GROUP)
        waitForText(FIRST_CHANNEL)
    }

    fun openFirstPlayer() {
        clickText(FIRST_CHANNEL)
        waitForDescription(FULLSCREEN_DESCRIPTION)
    }

    fun openFirstChannelActions() {
        waitForText(FIRST_CHANNEL).longClick()
        waitForText(GUIDE_LABEL)
    }

    fun exitPlayerToChannelList() {
        waitForDescription(BACK_DESCRIPTION)
        val playerBack = device.findObjects(By.desc(BACK_DESCRIPTION))
            .minByOrNull { it.visibleBounds.top }
        requireNotNull(playerBack) { "Player Back button was not found" }.click()
        waitForText(FIRST_CHANNEL)
    }

    fun clickText(text: String) = waitForText(text).click()

    fun clickDescription(description: String) = waitForDescription(description).click()

    fun waitForText(text: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.text(text)), timeoutMillis)) {
            "Timed out waiting for text '$text'"
        }

    fun waitForDescription(description: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.desc(description)), timeoutMillis)) {
            "Timed out waiting for description '$description'"
        }

    fun waitForEpgParseCompletion() {
        val status = waitForStatus(EPG_PARSE_STATUS_PATTERN, EPG_PARSE_TIMEOUT_MILLIS)
        check(status == EPG_PARSE_READY) { status }
    }

    private fun waitForStatus(pattern: Pattern, timeoutMillis: Long): String {
        val node = requireNotNull(device.wait(Until.findObject(By.text(pattern)), timeoutMillis)) {
            "Timed out waiting for benchmark status"
        }
        return node.text.orEmpty()
    }

    companion object {
        const val PACKAGE_NAME = "com.uacastplayer"
        const val MODE_PROFILE = "profile"
        const val MODE_UI_STRESS = "ui-stress"
        const val MODE_EPG_PARSE = "epg-parse"
        const val EPG_PARSE_COMPONENT = "$PACKAGE_NAME/.benchmark.EpgParseBenchmarkActivity"
        const val EPG_PARSE_READY = "benchmark-epg-parse-ready"
        const val EPG_TRACE_SECTION = "UaCastEpgParseAndIndex"
        const val HOME_LABEL = "Home"
        const val CHANNELS_LABEL = "Channels"
        const val FIRST_GROUP = "Benchmark Group 01"
        const val FIRST_CHANNEL = "Benchmark Channel 00001"
        const val FIRST_PROGRAMME = "Benchmark Programme 000"
        const val GUIDE_LABEL = "Guide"
        const val FULLSCREEN_DESCRIPTION = "Fullscreen"
        const val EXIT_FULLSCREEN_DESCRIPTION = "Exit fullscreen"
        const val BACK_DESCRIPTION = "Back"
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val EPG_PARSE_TIMEOUT_MILLIS = 180_000L

        private const val FIXTURE_COMPONENT = "$PACKAGE_NAME/.benchmark.BenchmarkFixtureActivity"
        private const val FIXTURE_MODE_EXTRA = "com.uacastplayer.benchmark.MODE"
        private const val FIXTURE_READY = "benchmark-fixture-ready"
        private const val FIXTURE_TIMEOUT_MILLIS = 180_000L
        private val FIXTURE_STATUS_PATTERN = Pattern.compile("benchmark-fixture-(ready|failed:.*)")
        private val EPG_PARSE_STATUS_PATTERN = Pattern.compile("benchmark-epg-parse-(ready|failed:.*)")
    }
}
