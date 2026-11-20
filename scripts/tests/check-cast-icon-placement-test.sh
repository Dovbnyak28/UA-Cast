#!/usr/bin/env bash
set -euo pipefail

readonly checker=scripts/check-cast-icon-placement.sh
readonly fixtures=scripts/tests/fixtures/cast-icon
checks=0

expect_status() {
  local expected=$1 actual=0 output
  shift
  output="$("$@" 2>&1)" || actual=$?
  if [[ "$actual" -ne "$expected" ]]; then
    printf 'Unexpected status %s (expected %s): %s\n' "$actual" "$expected" "$output" >&2
    exit 1
  fi
  if [[ "$expected" -ne 0 && "$output" == *'check-cast-icon-placement: OK'* ]]; then
    printf 'Failure must not advertise OK: %s\n' "$output" >&2
    exit 1
  fi
  checks=$((checks + 1))
}

expect_status 0 "$BASH" "$checker"
expect_status 0 "$BASH" "$checker" "$fixtures/allowed"
expect_status 1 "$BASH" "$checker" "$fixtures/denied"
expect_status 1 "$BASH" "$checker" "$fixtures/missing"
expect_status 1 "$BASH" "$checker" "$fixtures/allowed/player/Controls.kt"
expect_status 1 "$BASH" -c 'PATH=/nonexistent; source "$1" "$2"' _ "$checker" "$fixtures/allowed"
expect_status 1 "$BASH" -c 'grep() { return 2; }; export -f grep; source "$1" "$2"' _ "$checker" "$fixtures/allowed"
expect_status 1 "$BASH" -c '
  grep() { if [[ "$1" == -vE ]]; then return 2; else command grep "$@"; fi; }
  export -f grep; source "$1" "$2"
' _ "$checker" "$fixtures/allowed"
printf 'check-cast-icon-placement-test: OK (%s cases)\n' "$checks"
