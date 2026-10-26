package com.uacastplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
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

/** Device-side startup measurements kept next to the profile generator that prepares the app. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = measureStartup(StartupMode.COLD)

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM)

    private fun measureStartup(startupMode: StartupMode) {
        // Own the precondition: instrumentation test order is unspecified, so inheriting whatever
        // a 40k-channel journey or the first-run profile generator left behind makes cold/warm
        // numbers incomparable across runs. The profile fixture is representative but small.
        BenchmarkAppDriver(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
            .prepareFixture(BenchmarkAppDriver.MODE_PROFILE)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = startupMode,
            iterations = ITERATIONS,
            setupBlock = MacrobenchmarkScope::pressHome,
        ) {
            startActivityAndWait()
        }
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
