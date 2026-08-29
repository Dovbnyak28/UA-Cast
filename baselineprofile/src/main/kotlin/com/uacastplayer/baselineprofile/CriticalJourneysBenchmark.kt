package com.uacastplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
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
            driver.waitForText(PLAYLIST_NAME)
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
        journey = BenchmarkAppDriver::openFirstPlayer,
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
            driver.waitForText(BenchmarkAppDriver.FIRST_PROGRAMME)
        },
    )

    private fun measureUiJourney(
        setup: androidx.benchmark.macro.MacrobenchmarkScope.(BenchmarkAppDriver) -> Unit,
        journey: (BenchmarkAppDriver) -> Unit,
    ) {
        val driver = prepareStressFixture()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric(), maxMemoryMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                killProcess()
                setup(driver)
            },
        ) {
            journey(driver)
            device.waitForIdle()
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
        const val PLAYLIST_NAME = "Benchmark playlist"
    }
}
