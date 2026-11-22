package app.zorik.player.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The same catalog journeys as [CriticalJourneysBenchmark] at 10,000 channels - the size of a
 * typical real provider list, where the 40,000-channel stress fixture is the worst case. Having
 * both points shows whether cost grows with the catalog or is fixed overhead.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MediumCatalogBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldRestoreOfTenThousandChannels() {
        val driver = prepareMediumFixture()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
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
    fun openTenThousandChannelPlaylist() = measureUiJourney(
        setup = { driver -> driver.startMain(this) },
        journey = { driver -> driver.openChannels() },
    )

    @Test
    fun searchTenThousandChannels() = measureUiJourney(
        setup = { driver -> driver.startMain(this); driver.openChannels() },
        journey = BenchmarkAppDriver::searchTenThousandChannels,
    )

    private fun measureUiJourney(
        setup: MacrobenchmarkScope.(BenchmarkAppDriver) -> Unit,
        journey: (BenchmarkAppDriver) -> Unit,
    ) {
        val driver = prepareMediumFixture()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
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
            // Same bounded capture tail as CriticalJourneysBenchmark: idle is not a GPU fence.
            SystemClock.sleep(FRAME_CAPTURE_TAIL_MILLIS)
        }
    }

    private fun prepareMediumFixture() = BenchmarkAppDriver(
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
    ).also { it.prepareFixture(BenchmarkAppDriver.MODE_UI_MEDIUM) }

    private companion object {
        const val ITERATIONS = 5
        const val FRAME_CAPTURE_TAIL_MILLIS = 500L
        const val PLAYLIST_NAME = "Benchmark playlist"
    }
}
