#!/usr/bin/env bash
# Fails the build on any String.format(...)/"...".format(...) call in main sources that doesn't
# pass a Locale in the same call. Kotlin's String.format without a Locale uses the *device's*
# default locale, which silently swaps in non-ASCII digits (Arabic-indic, Bengali, ...) on affected
# locales - harmless for a purely human-read label, but data corruption for anything persisted or
# parsed back (see MiniJson.kt, Fingerprint.kt). A call that is genuinely fine to leave
# locale-sensitive (a human-read-only on-screen label) should carry a `// locale-ok` comment on the
# same line, which exempts it from this check - see ui/epg/EpgGuideSheet.kt for an example.
#
# Usage: scripts/check-locale-format.sh (run from the repo root)

set -euo pipefail

SRC_DIR="app/src/main"
# Matches "...%...".format( and String.format( - the two call shapes used in this codebase.
PATTERN='"[^"]*%[^"]*"\s*\.format\(|String\.format\('

matches=$(grep -RPn --include='*.kt' "$PATTERN" "$SRC_DIR" || true)

violations=""
if [ -n "$matches" ]; then
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        case "$line" in
            *Locale*) continue ;;
            *"// locale-ok"*) continue ;;
        esac
        violations="$violations$line"$'\n'
    done <<< "$matches"
fi

if [ -n "$violations" ]; then
    echo "Locale-independent formatting check failed - String.format(...)/\"...\".format(...) without a Locale:"
    echo ""
    echo "$violations"
    echo "Pass Locale.ROOT for machine-read/persisted output, or add a trailing '// locale-ok' comment for a deliberately locale-sensitive human-read-only label."
    exit 1
fi

echo "Locale-independent formatting check passed."
