#!/usr/bin/env bash
set -euo pipefail

pattern='BenchmarkFixtureActivity|EpgParseBenchmarkActivity'

# The benchmark target is the shipping application id. A default `pm clear` would erase a user's
# playlists and settings when someone runs Macrobenchmark on a real phone. Keep destructive reset
# available only as an explicit call-site opt-in (`clearPackage = true`).
if grep -Eq 'fun prepareFixture\(mode: String, clearPackage: Boolean = true\)' \
  baselineprofile/src/main/kotlin/com/uacastplayer/baselineprofile/BenchmarkAppDriver.kt; then
  echo 'Benchmark fixture preparation must not clear the target package by default' >&2
  exit 1
fi

shipping_manifests=(
  'app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml'
  'app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml'
)

throwaway_manifests=(
  'app/build/intermediates/merged_manifest/benchmarkRelease/processBenchmarkReleaseMainManifest/AndroidManifest.xml'
  'app/build/intermediates/merged_manifest/nonMinifiedRelease/processNonMinifiedReleaseMainManifest/AndroidManifest.xml'
)

for manifest in "${shipping_manifests[@]}"; do
  test -f "$manifest" || { echo "Missing merged manifest: $manifest" >&2; exit 1; }
  if grep -Eq "$pattern" "$manifest"; then
    echo "Benchmark fixture leaked into shipping manifest: $manifest" >&2
    exit 1
  fi
done

for manifest in "${throwaway_manifests[@]}"; do
  test -f "$manifest" || { echo "Missing merged manifest: $manifest" >&2; exit 1; }
  count=$(grep -Ec "$pattern" "$manifest" || true)
  if [[ "$count" -ne 2 ]]; then
    echo "Expected two fixture activities in $manifest, found $count" >&2
    exit 1
  fi
done

for source_dir in app/src/main app/src/debug app/src/release; do
  if [[ -d "$source_dir" ]] && grep -R -E -q "$pattern" "$source_dir"; then
    echo "Benchmark fixture reference leaked into shipping source set: $source_dir" >&2
    exit 1
  fi
done

echo 'Benchmark fixture isolation check passed.'
