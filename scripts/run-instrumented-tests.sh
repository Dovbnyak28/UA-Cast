#!/usr/bin/env bash
# Builds, installs and runs the instrumented suite against whatever device or emulator adb sees.
#
# Two things this does that `./gradlew :app:connectedDebugAndroidTest` does not:
#
# 1. It does not uninstall the app when it finishes. connectedDebugAndroidTest does, taking the
#    imported playlist, the EPG snapshot and the icon cache with it - which is destructive on a
#    phone carrying real data, and is why docs/RELEASING.md tells a human to use this route.
#
# 2. It goes through `am instrument` rather than Gradle's test runner, which sidesteps the
#    "Failed to receive the UTP test results" failure that makes connectedDebugAndroidTest report
#    FAILURE on this project while the device's own logcat shows the suite passing.
#
# The one trap it has to handle: `am instrument` exits 0 whether the tests passed or failed. A CI
# step that just runs it is green no matter what happens, which is worse than not running it at
# all. So the output is inspected, and the run is only a pass if the runner actually printed
# "OK (n tests)".
#
# Usage: scripts/run-instrumented-tests.sh (run from the repo root, with a device attached)

set -euo pipefail

PACKAGE="com.uacastplayer.debug"
RUNNER="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
APP_APK="app/build/outputs/apk/debug/app-universal-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

report_dir="app/build/reports/instrumented"
mkdir -p "$report_dir"
setup_log="$report_dir/setup.txt"
: > "$setup_log"
# This directory is generated test output, not application data. A failed setup must not upload
# the previous invocation's passing summary or crash buffer as evidence for the current run.
: > "$report_dir/runner.txt"
rm -f -- "$report_dir/crash-logcat.txt"
device_is_emulator=0
adb_timeout_seconds=${INSTRUMENTED_ADB_TIMEOUT_SECONDS:-30}
install_timeout_seconds=${INSTRUMENTED_INSTALL_TIMEOUT_SECONDS:-180}
runner_timeout_seconds=${INSTRUMENTED_RUNNER_TIMEOUT_SECONDS:-900}
for command_timeout in "$adb_timeout_seconds" "$install_timeout_seconds" "$runner_timeout_seconds"; do
    if ! [[ "$command_timeout" =~ ^[1-9][0-9]{0,3}$ ]] || [ "$command_timeout" -gt 3600 ]; then
        echo "run-instrumented-tests: timeouts must be integer seconds between 1 and 3600" >&2
        exit 1
    fi
done
if ! command -v timeout >/dev/null 2>&1; then
    echo "run-instrumented-tests: GNU timeout is required to bound device commands" >&2
    exit 1
fi

# Record phase names, not command arguments or device content. Even a hung install must leave
# evidence before the CI step's outer deadline. bash also permits the exported mock adb used by
# the contract tests; timeout terminates its process group if the transport stops responding.
run_adb() {
    local timeout_seconds=$1 phase=$2 command_status=0
    shift 2
    printf 'run-instrumented-tests: %s\n' "$phase" | tee -a "$setup_log" >&2
    timeout --kill-after=5s "${timeout_seconds}s" bash -c 'adb "$@"' -- "$@" || command_status=$?
    if [ "$command_status" -ne 0 ]; then
        printf 'run-instrumented-tests: %s failed with status %s\n' "$phase" "$command_status" \
            | tee -a "$setup_log" >&2
    fi
    return "$command_status"
}

# Install the failure handler before setup, not only after installation. A physical handset's
# crash buffer may contain unrelated private data, so only a positively identified emulator is
# eligible. Failure to capture diagnostics never replaces the original command's exit status.
capture_failed_emulator_crash() {
    local exit_status=$?
    if [ "$exit_status" -ne 0 ] && [ "$device_is_emulator" = "1" ]; then
        run_adb "$adb_timeout_seconds" "emulator crash diagnostics" -s "$DEVICE_SERIAL" logcat -b crash -d -v threadtime \
            > "$report_dir/crash-logcat.txt" 2>&1 || true
    fi
    return "$exit_status"
}
trap capture_failed_emulator_crash EXIT

# Process substitutions do not propagate adb's exit status to mapfile. Keep discovery failure
# distinct from a successful discovery that found no authorized device.
device_inventory=$(run_adb "$adb_timeout_seconds" "device discovery" devices)
mapfile -t authorized_devices < <(printf '%s\n' "$device_inventory" | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ "${#authorized_devices[@]}" -eq 0 ]; then
    echo "run-instrumented-tests: no authorized device or emulator attached" >&2
    exit 1
fi
if [ -n "${ADB_SERIAL:-}" ]; then
    if ! printf '%s\n' "${authorized_devices[@]}" | grep -Fxq "$ADB_SERIAL"; then
        printf 'run-instrumented-tests: ADB_SERIAL is not an authorized device: %s\n' \
            "$ADB_SERIAL" >&2
        printf 'run-instrumented-tests: authorized devices: %s\n' \
            "${authorized_devices[*]}" >&2
        exit 1
    fi
    DEVICE_SERIAL="$ADB_SERIAL"
elif [ "${#authorized_devices[@]}" -gt 1 ]; then
    printf 'run-instrumented-tests: expected one authorized device, found: %s\n' \
        "${authorized_devices[*]}" >&2
    echo "run-instrumented-tests: set ADB_SERIAL to choose one" >&2
    exit 1
else
    DEVICE_SERIAL="${authorized_devices[0]}"
fi

# Fixtures replace saved sources. Never run them against real phone data by default.
# The Windows helper moves files/preferences aside and restores them even on failure.
device_is_emulator=$(run_adb "$adb_timeout_seconds" "device type check" -s "$DEVICE_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')
if [ "$device_is_emulator" != "1" ] && [ "${ALLOW_DEVICE_DATA_REPLACEMENT:-0}" != "1" ]; then
    echo "Physical-device fixtures replace playlists. Use scripts/run-preserved-device-tests.ps1." >&2
    echo "Only for a disposable test device: explicitly set ALLOW_DEVICE_DATA_REPLACEMENT=1." >&2
    exit 1
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

# The debug variant is split per ABI (see the splits block in app/build.gradle.kts), so there is no
# plain app-debug.apk - the universal one is the only build that fits any device.
run_adb "$install_timeout_seconds" "app installation" -s "$DEVICE_SERIAL" install -r "$APP_APK"
run_adb "$install_timeout_seconds" "test installation" -s "$DEVICE_SERIAL" install -r "$TEST_APK"

# A locked or sleeping OEM handset can keep MainActivity resumed while hiding its window. That
# makes Compose tests time out without telling us anything about the app. Wake the display and
# dismiss only a non-secure keyguard; a secure lock is left untouched and the runner will report
# the actionable timeout instead of changing the user's lock settings. Stop both packages so a
# previous failed runner cannot leave a hidden Activity window behind.
run_adb "$adb_timeout_seconds" "display wake" -s "$DEVICE_SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
run_adb "$adb_timeout_seconds" "non-secure keyguard dismissal" -s "$DEVICE_SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
run_adb "$adb_timeout_seconds" "app stop" -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
run_adb "$adb_timeout_seconds" "test package stop" -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE.test" >/dev/null 2>&1 || true

echo "Running $RUNNER"
# Stream progress to CI and disk instead of retaining everything in a command substitution.
# If the emulator hangs or the job is cancelled, the completed tests remain diagnosable.
set +e
# Raw status includes each method's start/end, not just class-level dots. If a method hangs,
# the partial report identifies it without requiring unrelated device/private log buffers.
run_adb "$runner_timeout_seconds" "instrumentation" -s "$DEVICE_SERIAL" shell am instrument -w -r "$RUNNER" 2>&1 | tee "$report_dir/runner.txt"
runner_statuses=("${PIPESTATUS[@]}")
set -e
if [ "${runner_statuses[0]}" -ne 0 ]; then
    echo "run-instrumented-tests: adb runner command failed with status ${runner_statuses[0]}" >&2
    exit "${runner_statuses[0]}"
fi
if [ "${runner_statuses[1]}" -ne 0 ]; then
    echo "run-instrumented-tests: failed to persist the instrumentation report" >&2
    exit "${runner_statuses[1]}"
fi

if grep -q "FAILURES!!!" "$report_dir/runner.txt"; then
    echo "run-instrumented-tests: the suite reported failures" >&2
    exit 1
fi

if ! grep -Eq "OK \([0-9]+ tests?\)" "$report_dir/runner.txt"; then
    echo "run-instrumented-tests: the runner never reported a passing result - treating as failure" >&2
    exit 1
fi

echo "run-instrumented-tests: OK"
