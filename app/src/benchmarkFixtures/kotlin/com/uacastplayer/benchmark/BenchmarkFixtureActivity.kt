package com.uacastplayer.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prepares deterministic private storage for black-box benchmarks.
 *
 * This class is compiled only into `benchmarkRelease` and `nonMinifiedRelease`; the two variant
 * manifest overlays are the only place it is exported. Work stays off main so generating the
 * stress fixture cannot trip the Activity watchdog.
 */
class BenchmarkFixtureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).also { it.text = "benchmark-fixture-preparing" }
        setContentView(status)
        lifecycleScope.launch {
            val outcome = withContext(AppDispatchers.io) {
                runCatchingNonFatal {
                    when (intent.getStringExtra(BenchmarkFixtureContract.EXTRA_MODE)) {
                        BenchmarkFixtureContract.MODE_EPG_PARSE ->
                            BenchmarkFixtureInstaller.prepareEpgParseDocument(this@BenchmarkFixtureActivity)
                        BenchmarkFixtureContract.MODE_UI_STRESS ->
                            BenchmarkFixtureInstaller.installUiState(this@BenchmarkFixtureActivity, stress = true)
                        BenchmarkFixtureContract.MODE_PROFILE ->
                            BenchmarkFixtureInstaller.installUiState(this@BenchmarkFixtureActivity, stress = false)
                        else -> error("Unknown benchmark fixture mode")
                    }
                }
            }
            status.text = outcome.fold(
                onSuccess = { BenchmarkFixtureContract.FIXTURE_READY },
                onFailure = { "benchmark-fixture-failed:${it.javaClass.simpleName}" },
            )
        }
    }
}
