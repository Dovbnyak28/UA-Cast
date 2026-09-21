package com.uacastplayer.benchmark

/** IPC contract used only by the throwaway benchmark/profile APKs and their black-box driver. */
internal object BenchmarkFixtureContract {
    const val EXTRA_MODE = "com.uacastplayer.benchmark.MODE"
    const val MODE_PROFILE = "profile"
    const val MODE_UI_STRESS = "ui-stress"
    const val MODE_EPG_PARSE = "epg-parse"
    const val FIXTURE_READY = "benchmark-fixture-ready"
    const val EPG_PARSE_READY = "benchmark-epg-parse-ready"
    const val EPG_STRESS_FILE = "benchmark_epg_stress.xml"
}
