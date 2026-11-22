package app.zorik.player.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device measurements for the expensive journeys a fresh-install startup benchmark cannot see. */
@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class CriticalJourneysBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldRestoreOfFortyThousandChannels() {
        val driver = prepareStressFixture()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), maxMemoryMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            driver.waitForRestoredPlaylist(PLAYLIST_NAME)
        }
    }

    @Test
    fun openFortyThousandChannelPlaylist() = measureUiJourney(
        setup = { driver -> driver.startMain(this) },
        journey = { driver -> driver.openChannels() },
    )

    @Test
    fun firstPlayerLaunch() = measureUiJourney(
        setup = { driver ->
            driver.startMain(this)
            driver.openChannels()
            driver.openFirstGroup()
        },
        journey = { driver -> driver.openFirstPlayer(); driver.waitForVideoFrame() },
        firstFrame = true,
    )

    @Test
    fun switchPlayingChannel() = measureUiJourney(
        setup = { driver ->
            driver.startMain(this); driver.openChannels(); driver.openFirstGroup()
            driver.openFirstPlayer(); driver.waitForVideoFrame()
        },
        journey = BenchmarkAppDriver::switchChannel,
        firstFrame = true,
    )

    @Test
    fun searchFortyThousandChannels() = measureUiJourney(
        setup = { driver -> driver.startMain(this); driver.openChannels() },
        journey = BenchmarkAppDriver::searchFortyThousandChannels,
    )

    @Test
    fun scrollLargeChannelGroup() = measureUiJourney(
        setup = { driver -> driver.startMain(this); driver.openChannels(); driver.openFirstGroup() },
        journey = BenchmarkAppDriver::scrollChannelGroup,
    )

    @Test
    fun enterFullscreen() = measureUiJourney(
        setup = { driver ->
            driver.startMain(this)
            driver.openChannels()
            driver.openFirstGroup()
            driver.openFirstPlayer()
        },
        journey = { driver ->
            driver.clickDescription(BenchmarkAppDriver.FULLSCREEN_DESCRIPTION)
            driver.waitForDescription(BenchmarkAppDriver.EXIT_FULLSCREEN_DESCRIPTION)
        },
        observeFullscreen = true,
    )

    @Test
    fun openEpgGuide() = measureUiJourney(
        setup = { driver ->
            driver.startMain(this)
            driver.openChannels()
            driver.openFirstGroup()
            driver.openFirstChannelActions()
        },
        journey = { driver ->
            driver.clickText(BenchmarkAppDriver.GUIDE_LABEL)
            driver.waitForGuideProgramme()
        },
    )

    private fun measureUiJourney(
        setup: androidx.benchmark.macro.MacrobenchmarkScope.(BenchmarkAppDriver) -> Unit,
        journey: (BenchmarkAppDriver) -> Unit,
        firstFrame: Boolean = false,
        observeFullscreen: Boolean = false,
    ) {
        val driver = prepareStressFixture()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = buildList {
                add(FrameTimingMetric()); add(maxMemoryMetric())
                if (firstFrame) add(TraceSectionMetric("ZorikFirstVideoFrame", TraceSectionMetric.Mode.Sum))
            },
            compilationMode = CompilationMode.Partial(),
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                killProcess()
                setup(driver)
            },
        ) {
            val journeyStarted = SystemClock.elapsedRealtime()
            journey(driver)
            device.waitForIdle()
            if (observeFullscreen) {
                check(device.displayWidth > device.displayHeight) { "Fullscreen did not reach landscape" }
                val elapsed = SystemClock.elapsedRealtime() - journeyStarted
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(
                    2, android.os.Bundle().apply { putLong("fullscreenObservedJourneyMs", elapsed) },
                )
            }
            // Accessibility idle is not a GPU fence. The API-35 trace ended with the only
            // DrawFrames slice still open, so FrameTimingMetric had no completed frame to read.
            // Keep a bounded capture tail on the instrumentation thread; idle time does not
            // manufacture frame samples or change the P95/memory budget assertions.
            SystemClock.sleep(FRAME_CAPTURE_TAIL_MILLIS)
        }
    }

    private fun prepareStressFixture() = BenchmarkAppDriver(
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        ),
    ).also { it.prepareFixture(BenchmarkAppDriver.MODE_UI_STRESS) }

    private fun maxMemoryMetric() = MemoryUsageMetric(MemoryUsageMetric.Mode.Max)

    private companion object {
        const val ITERATIONS = 5
        const val FRAME_CAPTURE_TAIL_MILLIS = 500L
        const val PLAYLIST_NAME = "Benchmark playlist"
    }
}
