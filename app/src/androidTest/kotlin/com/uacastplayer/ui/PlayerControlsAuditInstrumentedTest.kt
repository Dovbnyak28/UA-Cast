package com.uacastplayer.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.player.FullscreenController
import com.uacastplayer.ui.player.PlayerControlsOverlay
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measurement fixture, not a pass/fail accessibility certification or a video-decoding benchmark.
 * Runs only production controls on a black surface; no playlist, receiver or network is used. */
@RunWith(AndroidJUnit4::class)
class PlayerControlsAuditInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun fullscreen() {
        rule.activityRule.scenario.onActivity { FullscreenController.apply(it, true) }
        rule.waitUntil(10_000) {
            rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        // Apply bars to the recreated Activity as production PlayerScreenEffects does.
        rule.activityRule.scenario.onActivity { FullscreenController.apply(it, true) }
    }

    @After fun restoreWindow() {
        rule.activityRule.scenario.onActivity {
            FullscreenController.apply(it, false)
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test fun controlsAtStandardFont() = inspect("fullscreen-100", 1f, true)
    @Test fun controlsWithLargeFontAndTimer() = inspect("fullscreen-200-timer", 2f, true, 3_600_000L)
    @Test fun idleSurfaceWithoutControls() = inspect("surface-only", 1f, false)

    private fun inspect(name: String, fontScale: Float, controls: Boolean, timer: Long? = null) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                UaCastTheme(AppTheme.CINEMA) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        if (controls) PlayerControlsOverlay(
                            uiState = PlayerUiState(
                                currentChannel = M3uChannel("News channel HD", "https://example.test/live"),
                                isPlaying = false,
                                wantsToPlay = true,
                                canControlPlayback = true,
                                canGoNext = true,
                                canGoPrevious = true,
                                nextChannelsPreview = (1..3).map {
                                    IndexedChannel(it, M3uChannel("Next channel $it", "https://example.test/$it"))
                                },
                            ),
                            isFullscreen = true,
                            sleepTimerRemainingMillis = remember { mutableStateOf(timer) },
                            onExit = {}, onPlayPause = {}, onNext = {}, onPrevious = {},
                            onToggleFullscreen = {}, onEnterPip = {}, onOpenSleepTimer = {},
                            isDlnaCasting = false, onOpenDlnaSheet = {}, onSelectPreview = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("player-controls-audit"))
        check(directory.isDirectory || directory.mkdirs())
        val density = rule.activity.resources.displayMetrics.density
        val report = StringBuilder("$name root=${rule.onRoot().fetchSemanticsNode().boundsInRoot} density=$density\n")
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val bounds = node.boundsInRoot
            val label = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()
                .ifEmpty { node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString() }
            report.appendLine("$label: bounds=$bounds visualDp=${bounds.width / density}x${bounds.height / density}")
        }
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        // Compose test clocks do not continuously drive infinite transitions between test calls.
        // Do not misrepresent this fixture as an idle CPU/FPS or video memory measurement.
        report.appendLine("fixtureTotalPssKb=${memory.totalPss}; excludes video decoder; not a peak measurement")
        File(directory, "$name.txt").writeText(report.toString())
        // Synchronize with the Compose root's draw, not an unrelated window/startup frame.
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
