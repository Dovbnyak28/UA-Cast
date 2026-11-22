#!/usr/bin/env bash
# Runs the measured macrobenchmark suite on the attached emulator while sampling process memory.
#
# Kept in a file because reactivecircus/android-emulator-runner executes every line of its `script:`
# input as a separate `sh -c`: a backslash-continued Gradle command became the task name '\', and
# `memory_pid=$!` / `trap` / `wait` lived in different shells, so the step failed in a second and
# then hung until the 45-minute timeout without producing a single measurement.
#
# Usage (repo root, one emulator attached): bash scripts/run-performance-benchmarks.sh
set -euo pipefail

serial="$(adb get-serialno)"
python3 scripts/sample-process-memory.py --serial "$serial" \
  --output baselineprofile/build/outputs/process-memory.json &
memory_pid=$!
stop_sampler() {
  kill -TERM "$memory_pid" 2>/dev/null || true
  wait "$memory_pid" 2>/dev/null || true
}
trap stop_sampler EXIT

package=app.zorik.player.baselineprofile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$package.StartupBenchmark,$package.CriticalJourneysBenchmark,$package.MediumCatalogBenchmark,$package.EpgParseBenchmark,$package.BenchmarkFixtureReadinessTest" \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  --stacktrace

# Stop the sampler explicitly so its final JSON is flushed before the budget step reads it.
trap - EXIT
kill -TERM "$memory_pid"
wait "$memory_pid"
