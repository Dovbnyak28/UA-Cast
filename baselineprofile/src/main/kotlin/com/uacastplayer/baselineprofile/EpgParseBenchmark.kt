package com.uacastplayer.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures production XMLTV parsing/indexing against 350,000 programmes on the actual device heap. */
@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class EpgParseBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun parseAndBuildIndex() {
        val driver = BenchmarkAppDriver(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
        driver.prepareFixture(BenchmarkAppDriver.MODE_EPG_PARSE)
        val parseComponent = requireNotNull(
            ComponentName.unflattenFromString(BenchmarkAppDriver.EPG_PARSE_COMPONENT),
        )
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppDriver.PACKAGE_NAME,
            metrics = listOf(
                TraceSectionMetric(BenchmarkAppDriver.EPG_TRACE_SECTION, TraceSectionMetric.Mode.Sum),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            compilationMode = CompilationMode.Partial(),
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                killProcess()
            },
        ) {
            startActivityAndWait(
                Intent().setComponent(parseComponent),
            )
            driver.waitForEpgParseCompletion()
        }
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
