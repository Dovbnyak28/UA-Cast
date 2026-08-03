#!/usr/bin/env bash
# Fails the build if any `override fun onCleared` doesn't call `super.onCleared()` somewhere in its
# body. ViewModel.onCleared()/AndroidViewModel.onCleared() are no-ops today, but that's not a
# contract this codebase should rely on staying true - skipping the super call is an easy accident
# (see PlayerViewModel.kt, which did exactly this until it was caught here) and would silently break
# the moment framework internals start doing real teardown in the base class.
#
# Usage: scripts/check-oncleared-super.sh (run from the repo root)

set -euo pipefail

SRC_DIR="app/src/main/kotlin"

violations=""
while IFS= read -r -d '' file; do
    result=$(awk '
        /override fun onCleared/ { in_fn = 1; depth = 0; entered = 0; found = 0; start = FNR }
        in_fn {
            nopen = gsub(/[{]/, "{")
            nclose = gsub(/[}]/, "}")
            if (nopen > 0) entered = 1
            depth += nopen - nclose
            if ($0 ~ /super\.onCleared\(\)/) found = 1
            if (entered && depth <= 0) {
                if (!found) print start
                in_fn = 0
            }
        }
    ' "$file")
    if [ -n "$result" ]; then
        while IFS= read -r ln; do
            [ -z "$ln" ] && continue
            violations="$violations$file:$ln"$'\n'
        done <<< "$result"
    fi
done < <(find "$SRC_DIR" -name '*.kt' -print0)

if [ -n "$violations" ]; then
    echo "onCleared() super check failed - override fun onCleared() without a super.onCleared() call:"
    echo ""
    echo "$violations"
    echo "Call super.onCleared() somewhere in the overriding onCleared() body."
    exit 1
fi

echo "onCleared() super check passed."
