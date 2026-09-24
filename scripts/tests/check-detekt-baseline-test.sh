#!/usr/bin/env bash
set -euo pipefail

readonly checker=scripts/check-detekt-baseline.sh
readonly fixtures=scripts/tests/fixtures
checks=0

expect_status() {
  local expected=$1 actual=0 output
  shift
  output="$("$@" 2>&1)" || actual=$?
  if [ "$actual" -ne "$expected" ]; then
    printf 'Unexpected status %s (expected %s): %s\n' "$actual" "$expected" "$output" >&2
    exit 1
  fi
  if [ "$expected" -ne 0 ] && [[ "$output" == *'OK (no suppressed findings)'* ]]; then
    printf 'Failure must not advertise OK: %s\n' "$output" >&2
    exit 1
  fi
  checks=$((checks + 1))
}

expect_status 0 "$BASH" "$checker" "$fixtures/baseline-empty.xml"
expect_status 1 "$BASH" "$checker" "$fixtures/baseline-issues.xml"
expect_status 1 "$BASH" "$checker" "$fixtures/does-not-exist.xml"
expect_status 1 "$BASH" "$checker" "$fixtures"
expect_status 1 "$BASH" -c 'PATH=/nonexistent; source "$1" "$2"' _ "$checker" "$fixtures/baseline-issues.xml"
expect_status 1 "$BASH" -c 'grep() { return 2; }; export -f grep; source "$1" "$2"' _ "$checker" "$fixtures/baseline-empty.xml"
expect_status 1 "$BASH" -c 'wc() { return 2; }; export -f wc; source "$1" "$2"' _ "$checker" "$fixtures/baseline-issues.xml"
expect_status 1 "$BASH" -c 'tr() { printf invalid; }; export -f tr; source "$1" "$2"' _ "$checker" "$fixtures/baseline-issues.xml"
printf 'check-detekt-baseline-test: OK (%s cases)\n' "$checks"
