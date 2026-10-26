#!/usr/bin/env bash
# Keep the Detekt baseline empty. A baseline is a temporary migration aid, not a way to hide
# newly introduced complexity or style regressions. If a rule is intentionally relaxed, encode
# that decision in config/detekt/detekt.yml with a comment instead.

set -euo pipefail

readonly BASELINE="${1:-config/detekt/baseline.xml}"

if [ ! -f "$BASELINE" ]; then
  echo "check-detekt-baseline: $BASELINE does not exist" >&2
  exit 1
fi

# Detekt writes every suppressed finding as an <ID> element in either CurrentIssues or
# ManuallySuppressedIssues. Count the tags instead of parsing the XML with a non-portable tool so
# this check behaves the same on Git Bash, Linux CI, and local Windows runs.
for dependency in grep wc tr; do
  if ! command -v "$dependency" >/dev/null 2>&1; then
    echo "check-detekt-baseline: required command $dependency is unavailable" >&2
    exit 1
  fi
done

# Keep assignment separate from readonly: the latter masks command-substitution failures.
# grep's no-match status is success for an empty baseline, but a read/tool error must fail closed.
if matches="$(grep -o '<ID>' -- "$BASELINE")"; then
  if ! ISSUE_COUNT="$(printf '%s\n' "$matches" | wc -l | tr -d '[:space:]')"; then
    echo 'check-detekt-baseline: could not count baseline findings' >&2
    exit 1
  fi
else
  grep_status=$?
  if [ "$grep_status" -ne 1 ]; then
    echo 'check-detekt-baseline: could not read baseline findings' >&2
    exit 1
  fi
  ISSUE_COUNT=0
fi
if [[ ! "$ISSUE_COUNT" =~ ^[0-9]+$ ]]; then
  echo 'check-detekt-baseline: invalid finding count' >&2
  exit 1
fi
readonly ISSUE_COUNT

if [ "$ISSUE_COUNT" -ne 0 ]; then
  echo "check-detekt-baseline: $BASELINE contains $ISSUE_COUNT suppressed issue(s)" >&2
  echo "Remove the finding or make the rule policy explicit in config/detekt/detekt.yml." >&2
  exit 1
fi

echo "check-detekt-baseline: OK (no suppressed findings)"
