#!/usr/bin/env bash
# Reports production Kotlin files longer than the project's practical ceiling (default 500 lines).
# Usage: scripts/check-max-file-lines.sh [max_lines]   -> exit 1 if any file exceeds the limit.
# Not wired into CI yet: enable it in android-ci.yml after the oversized files are split.
set -euo pipefail
cd "$(dirname "$0")/.."
max="${1:-500}"
status=0
while IFS= read -r -d '' f; do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$max" ]; then
    printf '%6d  %s\n' "$n" "$f"
    status=1
  fi
done < <(find app/src/main core/src/main -name '*.kt' -print0)
[ "$status" -eq 0 ] && echo "OK: no production Kotlin file exceeds $max lines"
exit "$status"
