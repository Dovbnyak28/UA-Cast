#!/usr/bin/env bash
# Fails the build if any AppLog.d/w/e call interpolates a variable whose name suggests it may
# carry a URL, token, or credential (see LogSanitizer, which also runs on every message as a
# second line of defense at runtime) - this catches the mistake at review time instead of relying
# on the sanitizer's regex heuristics alone to save a careless call site. A line that legitimately
# needs an identifier containing one of these substrings for a non-sensitive reason can opt out
# with a `// log-ok: <reason>` comment on the same line.
#
# Usage: scripts/check-applog-sensitive-vars.sh (run from the repo root)

set -euo pipefail

SRC_DIR="app/src/main/kotlin"
SENSITIVE_PATTERN='[$][{]?[A-Za-z0-9_.]*(url|uri|token|password)[A-Za-z0-9_.]*'

violations=""
while IFS= read -r -d '' file; do
    result=$(awk -v pattern="$SENSITIVE_PATTERN" '
        BEGIN { IGNORECASE = 1 }
        /AppLog\.(d|w|e)\(/ { in_call = 1; depth = 0; entered = 0 }
        in_call {
            nopen = gsub(/[{]/, "{")
            nclose = gsub(/[}]/, "}")
            if (nopen > 0) entered = 1
            depth += nopen - nclose
            if ($0 ~ pattern && $0 !~ /log-ok:/) print FNR
            if (entered && depth <= 0) in_call = 0
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
    echo "AppLog sensitive-variable check failed - a call interpolates a variable whose name"
    echo "suggests it may carry a URL, token, or credential:"
    echo ""
    echo "$violations"
    echo "Either stop interpolating the raw value, or if it's a false positive (the identifier"
    echo "just happens to contain one of these substrings), mark the line with // log-ok: <reason>."
    exit 1
fi

echo "AppLog sensitive-variable check passed."
