#!/usr/bin/env bash
# Runs :ffmpegdecoder:connectedDebugAndroidTest, retrying once only when the test APK failed to install.
#
# On the API 24 CI emulator the package manager sometimes rejects an install outright
# ("Failed to install-write all apks") after the app suite has already passed. That is an emulator
# fault, not a decoder failure, so it gets one more attempt after the device settles. Any other
# failure - including a failing decoder test - is reported as is, without a retry.
#
# Usage (repo root, one emulator attached): bash scripts/run-decoder-instrumented-tests.sh
set -uo pipefail

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

run() {
  ./gradlew :ffmpegdecoder:connectedDebugAndroidTest --stacktrace 2>&1 | tee "$log"
  return "${PIPESTATUS[0]}"
}

run
status=$?
if [ "$status" -ne 0 ] && grep -qE "Failed to install|InstallException" "$log"; then
  echo "run-decoder-instrumented-tests: test APK install failed; retrying once"
  adb wait-for-device
  adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
  run
  status=$?
fi
exit "$status"
