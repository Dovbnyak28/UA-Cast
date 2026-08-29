package com.uacastplayer.benchmark

import android.os.Bundle
import android.os.Trace
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.data.epg.withEpgCpu
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgDocumentPipeline
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Runs the real XMLTV CPU pipeline over the prepared 350k-programme document. */
class EpgParseBenchmarkActivity : ComponentActivity() {

    private var retainedData: EpgData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).also { it.text = "benchmark-epg-parse-running" }
        setContentView(status)
        lifecycleScope.launch {
            val outcome = withEpgCpu {
                runCatchingNonFatal {
                    Trace.beginSection(TRACE_SECTION)
                    try {
                        EpgDocumentPipeline.parse(
                            rawInput = File(filesDir, BenchmarkFixtureContract.EPG_STRESS_FILE).inputStream(),
                            nowMillis = System.currentTimeMillis(),
                            zoneId = ZoneId.systemDefault(),
                            maxHeapBytes = Runtime.getRuntime().maxMemory(),
                        )
                    } finally {
                        Trace.endSection()
                    }
                }
            }
            outcome.fold(
                onSuccess = { data ->
                    retainedData = data
                    status.text = BenchmarkFixtureContract.EPG_PARSE_READY
                },
                onFailure = { status.text = "benchmark-epg-parse-failed:${it.javaClass.simpleName}" },
            )
        }
    }

    internal companion object {
        const val TRACE_SECTION = "UaCastEpgParseAndIndex"
    }
}
