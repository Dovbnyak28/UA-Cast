#!/usr/bin/env bash
# Fails the build on a hardcoded Color(0x...) literal anywhere under ui/ outside ui/theme/. Every
# color a screen or component draws must come from UaTheme.palette (see
# app/src/main/kotlin/com/uacastplayer/ui/theme/UaPalette.kt and docs/DESIGN_SYSTEM.md "Themes"),
# so that a second theme (Cinema) - or any future one - only ever means a new UaPalette value, not
# a hunt through every screen for a color that forgot to go through the palette. ui/theme/ itself
# is exempt: that's where the actual palette values (Color.kt, CinemaPalette.kt, UaPalette.kt's
# scrim/glass fields) are allowed to live.
#
# Usage: scripts/check-no-hardcoded-colors.sh (run from the repo root)

set -euo pipefail

SRC_DIR="app/src/main/kotlin/com/uacastplayer/ui"
PATTERN='Color\(0x'

matches=$(grep -RPn --include='*.kt' "$PATTERN" "$SRC_DIR" || true)

violations=""
if [ -n "$matches" ]; then
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        case "$line" in
            */ui/theme/*) continue ;;
        esac
        violations="$violations$line"$'\n'
    done <<< "$matches"
fi

if [ -n "$violations" ]; then
    echo "Hardcoded color check failed - Color(0x...) literal outside ui/theme/:"
    echo ""
    echo "$violations"
    echo "Add the color as a UaPalette field (see UaPalette.kt/CinemaPalette.kt) and read it via UaTheme.palette.* instead."
    exit 1
fi

echo "Hardcoded color check passed."
