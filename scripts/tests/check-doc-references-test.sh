#!/usr/bin/env bash
set -euo pipefail
readonly checker=scripts/check-doc-references.sh
readonly fixture=scripts/tests/fixtures/baseline-empty.xml

expect_status() {
  local expected=$1 actual=0 output
  shift
  output="$("$@" 2>&1)" || actual=$?
  if [ "$actual" -ne "$expected" ]; then
    printf 'Unexpected status %s (expected %s): %s\n' "$actual" "$expected" "$output" >&2
    exit 1
  fi
  if [ "$expected" -ne 0 ] && [[ "$output" == *'check-doc-references: OK'* ]]; then
    printf 'Failure must not advertise OK: %s\n' "$output" >&2
    exit 1
  fi
}

expect_status 0 "$BASH" "$checker" "$fixture"
expect_status 1 "$BASH" "$checker" scripts/tests/fixtures/legacy-reference.txt
expect_status 1 "$BASH" "$checker" scripts/tests/fixtures/does-not-exist
expect_status 1 "$BASH" -c 'PATH=/nonexistent; source "$1" "$2"' _ "$checker" "$fixture"
expect_status 1 "$BASH" -c 'rg() { return 2; }; export -f rg; source "$1" "$2"' _ "$checker" "$fixture"
printf 'check-doc-references-test: OK (5 cases)\n'
