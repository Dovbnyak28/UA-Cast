package app.zorik.player.ui

import android.graphics.Bitmap
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.uiautomator.UiDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.zorik.player.R
import app.zorik.player.epg.EpgChannel
import app.zorik.player.epg.EpgData
import app.zorik.player.epg.EpgIndex
import app.zorik.player.epg.EpgProgramme
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.ui.epg.EpgGuideSheet
import app.zorik.player.ui.player.PlayerControlsOverlay
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.UaPlayerTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android rendering of production presentation; media decoding is tested separately. */
@RunWith(AndroidJUnit4::class)
class CinemaPlayerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val theme = mutableStateOf(AppTheme.CINEMA)
    private val guide = mutableStateOf(false)
    private val visible = mutableStateOf(true)
    private val timer = mutableStateOf<Long?>(null)
    private val start = Instant.parse("2026-11-14T09:00:00Z").toEpochMilli()
    private val channel = M3uChannel("Cinema — a long channel name HD", "https://example.test/live", tvgId = "cinema")
    private val programme = EpgProgramme("cinema", start, start + 3_600_000, "A quiet journey through the mountains")
    private val data = EpgData(EpgIndex(listOf(EpgChannel("cinema", listOf(channel.displayName), null))),
        mapOf("cinema" to listOf(programme, programme.copy(startMillis = start + 3_600_000,
            stopMillis = start + 7_200_000, title = "Evening cinema"))))

    @Before fun verifyRequestedDeviceConfiguration() {
        val arguments = InstrumentationRegistry.getArguments()
        val config = rule.activity.resources.configuration
        arguments.getString("expectedWidthDp")?.toInt()?.let { assertEquals(it, config.screenWidthDp) }
        arguments.getString("expectedFontScale")?.toFloat()?.let { assertEquals(it, config.fontScale, 0.01f) }
        println("Cinema Player device: ${config.screenWidthDp}x${config.screenHeightDp} font=${config.fontScale}")
        rule.activityRule.scenario.onActivity {
            WindowInsetsControllerCompat(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Test fun everyThemeKeepsTransportAndDirectGuideReachable() {
        render()
        for (nextTheme in AppTheme.entries) {
            rule.runOnIdle { theme.value = nextTheme }
            rule.waitForIdle()
            assertTargets()
            screenshot("player-${nextTheme.name.lowercase()}")
        }
        rule.runOnIdle { theme.value = AppTheme.CINEMA }
        rule.runOnIdle { timer.value = 3_600_000L }
        assertTargets()
        rule.onNodeWithText("60:00").assertIsDisplayed()
        screenshot("player-active-timer")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.player_tv_guide)).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(rule.activity.getString(R.string.epg_now)) and hasClickAction()).assertIsDisplayed()
        val listBounds = rule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("EPG chrome leaves insufficient reading space", (listBounds.bottom - listBounds.top).value >= 120f)
        val currentTitle = rule.onNode(hasText(programme.title) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        val titleBounds = currentTitle.getUnclippedBoundsInRoot()
        val titleHeight = (titleBounds.bottom - titleBounds.top).value * rule.density.density
        assertTrue("Current programme title is clipped", currentTitle.fetchSemanticsNode().boundsInRoot.height + 1 >=
            titleHeight)
        screenshot("epg")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.epg_next_day)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.epg_jump_today)).assertIsDisplayed()
        screenshot("epg-next-day")
        rule.onNodeWithText(rule.activity.getString(R.string.epg_jump_today)).performClick()
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitForIdle()
        rule.waitUntil(5_000) { !guide.value }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.player_tv_guide)).assertExists()
        screenshot("player-after-epg")
    }

    @Test fun repeatedPresentationReportsBoundedUiCost() {
        render()
        val samples = mutableListOf<Long>()
        repeat(30) {
            val begin = System.nanoTime()
            rule.runOnIdle { visible.value = false }
            rule.waitForIdle()
            rule.runOnIdle { visible.value = true }
            rule.waitForIdle()
            samples += (System.nanoTime() - begin) / 1_000_000
        }
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val ordered = samples.sorted()
        File(directory(), "presentation-measurement.txt").writeText(
            "overlay hide+show with test synchronization; no video decoder\n" +
                "widthDp=${rule.activity.resources.configuration.screenWidthDp} " +
                "fontScale=${rule.activity.resources.configuration.fontScale}\n" +
                "samplesMs=$samples\nmedianMs=${ordered[ordered.size / 2]} " +
                "p90Ms=${ordered[(ordered.size * 9 / 10)]}\nfixturePssKb=${memory.totalPss}\n" +
                "Not an FPS/peak-heap or device playback benchmark.\n",
        )
        assertTargets()
    }

    private fun render() {
        rule.setContent { ZorikTheme(theme.value) { UaPlayerTheme {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (visible.value) PlayerControlsOverlay(
                    PlayerUiState(currentChannel = channel, wantsToPlay = true, canControlPlayback = true,
                        canGoNext = true, canGoPrevious = true), true, timer,
                    {}, {}, {}, {}, {}, {}, {}, false, {}, {}, programme = programme,
                    programmeNowMillis = start + 1_800_000, onOpenGuide = { guide.value = true },
                )
                if (guide.value) EpgGuideSheet(channel, data, start + 1_800_000, { guide.value = false })
            }
        } } }
    }

    private fun assertTargets() {
        val root = rule.onAllNodes(isRoot()).onLast().fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val bounds = node.boundsInRoot
            assertTrue("A control is clipped: $bounds in $root", bounds.left >= root.left && bounds.top >= root.top &&
                bounds.right <= root.right && bounds.bottom <= root.bottom)
            assertTrue("Control target below 48dp: $bounds", bounds.width / density >= 47.9f &&
                bounds.height / density >= 47.9f)
        }
    }

    private fun directory(): File = checkNotNull(InstrumentationRegistry.getInstrumentation()
        .targetContext.getExternalFilesDir("cinema-v2-stage4")).also { check(it.isDirectory || it.mkdirs()) }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        // Capture the actual top window, including the EPG dialog, not a background Compose root.
        val frame = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory(), "$name.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        frame.recycle()
    }
}
