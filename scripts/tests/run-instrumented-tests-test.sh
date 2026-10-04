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
            return "$MOCK_RUNNER_EXIT"
            ;;
        'logcat -b crash') printf 'synthetic platform crash evidence\n' ;;
        *) return 0 ;;
    esac
}
export -f adb
export MOCK_QEMU MOCK_RUNNER_OUTPUT MOCK_RUNNER_EXIT
unset ADB_SERIAL
export ALLOW_DEVICE_DATA_REPLACEMENT=1

assert_case() {
    local name=$1 expected_status=$2 expected_crash=$3
    local case_dir="$fixture_dir/$name"
    mkdir -p "$case_dir"
    cp "$fixture_dir/runner.sh" "$fixture_dir/gradlew" "$case_dir/"
    local actual_status=0
    (cd "$case_dir" && bash runner.sh) > "$case_dir/test-output.txt" 2>&1 || actual_status=$?
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
printf 'Instrumented runner contract: 6 cases passed\n'
