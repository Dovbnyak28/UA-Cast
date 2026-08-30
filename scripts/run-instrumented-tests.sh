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

mapfile -t authorized_devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
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

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

# The debug variant is split per ABI (see the splits block in app/build.gradle.kts), so there is no
# plain app-debug.apk - the universal one is the only build that fits any device.
adb -s "$DEVICE_SERIAL" install -r "$APP_APK"
adb -s "$DEVICE_SERIAL" install -r "$TEST_APK"

echo "Running $RUNNER"
output=$(adb -s "$DEVICE_SERIAL" shell am instrument -w "$RUNNER" 2>&1)
echo "$output"

if printf '%s' "$output" | grep -q "FAILURES!!!"; then
    echo "run-instrumented-tests: the suite reported failures" >&2
    exit 1
fi

if ! printf '%s' "$output" | grep -qE "OK \([0-9]+ tests?\)"; then
    echo "run-instrumented-tests: the runner never reported a passing result - treating as failure" >&2
    exit 1
fi

echo "run-instrumented-tests: OK"
