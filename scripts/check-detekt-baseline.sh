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
readonly ISSUE_COUNT="$(grep -o '<ID>' "$BASELINE" | wc -l | tr -d '[:space:]')"

if [ "$ISSUE_COUNT" -ne 0 ]; then
  echo "check-detekt-baseline: $BASELINE contains $ISSUE_COUNT suppressed issue(s)" >&2
  echo "Remove the finding or make the rule policy explicit in config/detekt/detekt.yml." >&2
  exit 1
fi

echo "check-detekt-baseline: OK (no suppressed findings)"
