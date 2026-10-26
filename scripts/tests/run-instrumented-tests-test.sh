#!/usr/bin/env bash
# Destructive-output contract tests with mocked adb; no Android device is contacted.
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
mkdir -p "$repo_root/build"
fixture_dir=$(mktemp -d "$repo_root/build/instrumented-runner-test.XXXXXX")
cp "$repo_root/scripts/run-instrumented-tests.sh" "$fixture_dir/runner.sh"
cp "$repo_root/scripts/tests/fixtures/instrumented-gradlew-stub.sh" "$fixture_dir/gradlew"
chmod +x "$fixture_dir/gradlew"

adb() {
    if [ "${1:-}" = devices ]; then
        if [ "${MOCK_HANG_PHASE:-}" = discovery ]; then sleep 30; fi
        if [ "${MOCK_DEVICES_EXIT:-0}" -ne 0 ]; then return "$MOCK_DEVICES_EXIT"; fi
        printf 'List of devices attached\nemulator-5554\tdevice\n'
        return 0
    fi
    if [ "${1:-}" = -s ]; then shift 2; fi
    case "${1:-} ${2:-} ${3:-}" in
        'shell getprop ro.kernel.qemu') printf '%s\n' "$MOCK_QEMU" ;;
        'shell am instrument')
            if [[ " $* " != *" -w -r "* ]]; then
                printf 'Raw per-method progress is required\n' >&2
                return 19
            fi
            printf '%s\n' "$MOCK_RUNNER_OUTPUT"
            if [ "${MOCK_HANG_PHASE:-}" = runner ]; then sleep 30; fi
            return "$MOCK_RUNNER_EXIT"
            ;;
        'logcat -b crash') printf 'synthetic platform crash evidence\n' ;;
        *)
            if [ "${1:-}" = install ] && [ "${MOCK_HANG_PHASE:-}" = install ]; then sleep 30; fi
            return 0
            ;;
    esac
}
export -f adb
export MOCK_QEMU MOCK_RUNNER_OUTPUT MOCK_RUNNER_EXIT
export MOCK_HANG_PHASE='' MOCK_DEVICES_EXIT=0
export INSTRUMENTED_ADB_TIMEOUT_SECONDS=1 INSTRUMENTED_INSTALL_TIMEOUT_SECONDS=1
export INSTRUMENTED_RUNNER_TIMEOUT_SECONDS=1
unset ADB_SERIAL
export ALLOW_DEVICE_DATA_REPLACEMENT=1

assert_case() {
    local name=$1 expected_status=$2 expected_crash=$3
    local case_dir="$fixture_dir/$name"
    mkdir -p "$case_dir"
    cp "$fixture_dir/runner.sh" "$fixture_dir/gradlew" "$case_dir/"
    if [ "${4:-}" = stale ]; then
        local reports="$case_dir/app/build/reports/instrumented"
        mkdir -p "$reports"
        printf 'OK (9999 tests)\n' > "$reports/runner.txt"
        printf 'synthetic evidence from a previous run\n' > "$reports/crash-logcat.txt"
    fi
    local actual_status=0
    (cd "$case_dir" && timeout --kill-after=1s 8s bash runner.sh) > "$case_dir/test-output.txt" 2>&1 || actual_status=$?
    if [ "$actual_status" -ne "$expected_status" ]; then
        printf '%s: expected status %s, got %s\n' "$name" "$expected_status" "$actual_status" >&2
        exit 1
    fi
    local crash_path="$case_dir/app/build/reports/instrumented/crash-logcat.txt"
    if [ "$expected_crash" = yes ]; then
        grep -Fxq 'synthetic platform crash evidence' "$crash_path"
    elif [ -e "$crash_path" ]; then
        printf '%s: must not capture a passing run or physical-device crash buffer\n' "$name" >&2
        exit 1
    fi
}

MOCK_QEMU=1 MOCK_RUNNER_EXIT=0 MOCK_RUNNER_OUTPUT='OK (142 tests)'
assert_case pass 0 no
MOCK_RUNNER_OUTPUT=$'INSTRUMENTATION_STATUS: test=syntheticMethod\nINSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stream=\nOK (1 test)'
assert_case raw_summary 0 no
MOCK_RUNNER_OUTPUT='INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
assert_case crashed 1 yes
MOCK_RUNNER_OUTPUT=$'FAILURES!!!\nOK (142 tests)'
assert_case failures_even_with_ok 1 yes
MOCK_RUNNER_OUTPUT='transport disconnected' MOCK_RUNNER_EXIT=17
assert_case transport_failure 17 yes
MOCK_QEMU=0 MOCK_RUNNER_EXIT=0 MOCK_RUNNER_OUTPUT='Process crashed.'
assert_case physical_privacy 1 no
MOCK_QEMU=1 MOCK_HANG_PHASE=install
assert_case hanging_install 124 yes
grep -Fq 'app installation failed with status 124' "$fixture_dir/hanging_install/app/build/reports/instrumented/setup.txt"
MOCK_HANG_PHASE=runner MOCK_RUNNER_OUTPUT='INSTRUMENTATION_STATUS: test=unfinishedSyntheticMethod'
assert_case hanging_runner 124 yes
grep -Fq 'unfinishedSyntheticMethod' "$fixture_dir/hanging_runner/app/build/reports/instrumented/runner.txt"
MOCK_HANG_PHASE=discovery
assert_case hanging_discovery 124 no
grep -Fq 'device discovery failed with status 124' "$fixture_dir/hanging_discovery/app/build/reports/instrumented/setup.txt"
MOCK_HANG_PHASE='' MOCK_DEVICES_EXIT=18
assert_case discovery_transport_failure 18 no
grep -Fq 'device discovery failed with status 18' "$fixture_dir/discovery_transport_failure/app/build/reports/instrumented/setup.txt"
MOCK_DEVICES_EXIT=0 MOCK_RUNNER_OUTPUT='OK (1 test)'
assert_case stale_pass 0 no stale
MOCK_HANG_PHASE=install
assert_case stale_install 124 yes stale
if [ -s "$fixture_dir/stale_install/app/build/reports/instrumented/runner.txt" ]; then
    echo 'A failed setup must not retain a previous passing runner report' >&2
    exit 1
fi
printf 'Instrumented runner contract: 12 cases passed\n'
