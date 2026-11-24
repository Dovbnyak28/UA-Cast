package app.zorik.player.baselineprofile

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
     * The target package is the real `app.zorik.player` application, not a benchmark-only clone.
     * A blanket `pm clear` used to be the default here, which made running a macrobenchmark on a
     * connected phone silently delete its playlists, favourites and settings. The benchmark
     * fixture overwrites the active source and the files it owns, so a full clear is unnecessary
     * for normal runs. Keep the escape hatch for a deliberately dedicated device, but make the
     * destructive choice visible at every call site.
     */
    fun prepareFixture(mode: String, clearPackage: Boolean = false) {
        // Fixture preparation precedes measureRepeated, so Macrobenchmark's own wake-up is too late.
        // Dismiss only a non-secure keyguard; this does not unlock a device protected by a PIN.
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        if (clearPackage) {
            val clearResult = device.executeShellCommand("pm clear $PACKAGE_NAME")
            check(clearResult.contains("Success")) { "Could not clear benchmark package" }
        } else {
            device.executeShellCommand("am force-stop $PACKAGE_NAME")
        }
        val launchResult = device.executeShellCommand(
            "am start -W -n $FIXTURE_COMPONENT --es $FIXTURE_MODE_EXTRA $mode",
        )
        check(launchResult.lineSequence().none { it.startsWith("Error:") || it.startsWith("Error type ") }) {
            "Could not start benchmark fixture: $launchResult"
        }
        val status = waitForStatus(FIXTURE_STATUS_PATTERN, FIXTURE_TIMEOUT_MILLIS)
        check(status == FIXTURE_READY) { status }
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
    }

    fun startMain(scope: MacrobenchmarkScope) {
        scope.startActivityAndWait()
        // Home now displays "Overview" and the app-name heading. Its accessibility description
        // remains "Home", matching the Channels selector; visible wording is not a ready signal.
        waitForDescription(HOME_LABEL)
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

    fun waitForVideoFrame() { waitForDescription("Video frame displayed") }

    /**
     * Turns the device to landscape before the player opens, so a fullscreen measurement covers the
     * switch into the fullscreen overlay alone. Entering fullscreen from portrait also rotates the
     * display, and those rotation frames - about 5% of the window, i.e. exactly the p95 - swung
     * `enterFullscreen frameDurationCpuMs/p95` between 60 and 120 ms on the CI emulator for the
     * same code. Rotating here, on the app's home screen, keeps the player's controls in their
     * just-opened, visible state: a player rotated while open had already hidden them, and taps on
     * the video did not bring them back reliably for UiAutomator.
     */
    fun rotateToLandscape() {
        device.setOrientationLandscape()
        check(pollUntil(ROTATION_TIMEOUT_MILLIS) { device.displayWidth > device.displayHeight }) {
            "Device did not rotate to landscape"
        }
        device.waitForIdle()
    }

    /** Undoes [rotateToLandscape]: natural orientation, then the user's rotation setting. */
    fun restoreNaturalOrientation() {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    private fun pollUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    fun switchChannel() {
        clickText("Next channel")
        waitForDescription("Now playing: Benchmark Channel 00002")
        waitForVideoFrame()
    }

    fun searchFortyThousandChannels() = searchChannel("Benchmark Channel 39999")

    fun searchTenThousandChannels() = searchChannel("Benchmark Channel 09999")

    private fun searchChannel(name: String) {
        val input = requireNotNull(device.wait(
            Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MILLIS,
        )) { "Global channel search field was not found" }
        input.text = name
        waitForText(name)
    }

    fun scrollChannelGroup() {
        val list = requireNotNull(device.findObjects(By.scrollable(true))
            .filter { it.className != "android.widget.EditText" }
            .maxByOrNull { it.visibleBounds.height() }) { "Channel list was not scrollable" }
        list.fling(androidx.test.uiautomator.Direction.DOWN)
        check(device.wait(Until.gone(By.text(FIRST_CHANNEL)), UI_TIMEOUT_MILLIS)) {
            "Fling did not move the first channel out of view"
        }
        device.waitForIdle()
    }

    /**
     * Long-presses the first channel until its action sheet shows "Guide".
     *
     * The channel row appears while the group screen is still entering; a long press landing in
     * that window is cancelled by the moving list and no sheet opens (seen on the API 30 emulator
     * 66 ms after the group click). Waiting for the UI to settle and retrying the press keeps this
     * setup step reliable; it runs outside the measured journey.
     */
    fun openFirstChannelActions() {
        repeat(CHANNEL_ACTION_ATTEMPTS) {
            device.waitForIdle()
            waitForText(FIRST_CHANNEL).longClick()
            if (device.wait(Until.hasObject(By.text(GUIDE_LABEL)), CHANNEL_ACTION_TIMEOUT_MILLIS)) return
        }
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

    fun waitForRestoredPlaylist(name: String) {
        // The compact source card is above the primary action and only mounts after hasChannels.
        // Navigation chrome or a hidden channel preview cannot satisfy playlist restoration.
        requireNotNull(device.wait(
            Until.findObject(By.pkg(PACKAGE_NAME).text(name)), UI_TIMEOUT_MILLIS,
        )) { "Timed out waiting for the restored benchmark playlist card" }
    }

    fun waitForEpgParseCompletion() {
        val status = waitForStatus(EPG_PARSE_STATUS_PATTERN, EPG_PARSE_TIMEOUT_MILLIS)
        check(status == EPG_PARSE_READY) { status }
    }

    fun waitForGuideProgramme() {
        // The guide selects today and scrolls to the current programme. Slot 000 belongs to
        // midnight, so requiring it to be visible makes this journey fail later in the day.
        requireNotNull(device.wait(
            Until.findObject(By.pkg(PACKAGE_NAME).text(GUIDE_PROGRAMME_PATTERN)), UI_TIMEOUT_MILLIS,
        )) { "Timed out waiting for a visible benchmark guide programme" }
    }

    private fun waitForStatus(pattern: Pattern, timeoutMillis: Long): String {
        val node = requireNotNull(device.wait(Until.findObject(By.text(pattern)), timeoutMillis)) {
            // Report only lifecycle/setup state and our fixed synthetic status tokens, not a
            // whole window hierarchy (a physical benchmark device can contain private content).
            val visibleStatus = device.findObjects(By.text(VISIBLE_STATUS_PATTERN))
                .map { it.text.orEmpty() }
            "Timed out waiting for benchmark status: screenOn=${device.isScreenOn}, " +
                "foreground=${device.currentPackageName}, visibleStatus=$visibleStatus"
        }
        return node.text.orEmpty()
    }

    companion object {
        const val PACKAGE_NAME = "app.zorik.player"
        const val MODE_PROFILE = "profile"
        const val MODE_UI_STRESS = "ui-stress"
        const val MODE_UI_MEDIUM = "ui-medium"
        const val MODE_EPG_PARSE = "epg-parse"
        const val EPG_PARSE_COMPONENT = "$PACKAGE_NAME/.benchmark.EpgParseBenchmarkActivity"
        const val EPG_PARSE_READY = "benchmark-epg-parse-ready"
        const val EPG_TRACE_SECTION = "ZorikEpgParseAndIndex"
        const val HOME_LABEL = "Home"
        const val CHANNELS_LABEL = "Channels"
        const val FIRST_GROUP = "Benchmark Group 01"
        const val FIRST_CHANNEL = "Benchmark Channel 00001"
        const val GUIDE_LABEL = "Guide"
        const val FULLSCREEN_DESCRIPTION = "Fullscreen"
        const val EXIT_FULLSCREEN_DESCRIPTION = "Exit fullscreen"
        private const val ROTATION_TIMEOUT_MILLIS = 10_000L
        private const val POLL_MILLIS = 100L
        const val BACK_DESCRIPTION = "Back"
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val EPG_PARSE_TIMEOUT_MILLIS = 180_000L
        private const val CHANNEL_ACTION_ATTEMPTS = 3
        private const val CHANNEL_ACTION_TIMEOUT_MILLIS = 5_000L

        private const val FIXTURE_COMPONENT = "$PACKAGE_NAME/.benchmark.BenchmarkFixtureActivity"
        private const val FIXTURE_MODE_EXTRA = "app.zorik.player.benchmark.MODE"
        private const val FIXTURE_READY = "benchmark-fixture-ready"
        private const val FIXTURE_TIMEOUT_MILLIS = 180_000L
        private val FIXTURE_STATUS_PATTERN = Pattern.compile("benchmark-fixture-(ready|failed:.*)")
        private val EPG_PARSE_STATUS_PATTERN = Pattern.compile("benchmark-epg-parse-(ready|failed:.*)")
        private val VISIBLE_STATUS_PATTERN =
            Pattern.compile("benchmark-(fixture|epg-parse)-(preparing|ready|failed:.*)")
        private val GUIDE_PROGRAMME_PATTERN = Pattern.compile("Benchmark Programme [0-9]{3}")
    }
}
