#!/usr/bin/env bash
# Fails the build if a `*Glow` palette color is used as a foreground - a text `color =` or an icon
# `tint =`.
#
# The glow fields are the route-health hues at ~50% alpha, which is what makes a halo read as a halo
# behind a status dot. As a foreground they composite against the background instead of covering it:
# `amberGlow` on true black lands at #806B05, 3.99:1, under WCAG AA for body text. The settings
# screen's EPG-truncation warning shipped exactly that, and it took adding a theme whose background
# is actually #000000 for anyone to see it - on the two textured near-black themes it merely looked
# a bit dim. The correct foregrounds are routeGreen/routeAmber/routeRed and accentText.
#
# Usage: scripts/check-glow-not-text.sh (run from the repo root)

set -euo pipefail

SRC_DIR="app/src/main/kotlin"

# Deliberately narrow: only `color =` and `tint =`, the two parameter names that mean "foreground"
# in Compose. `.background(...Glow)`, `spotColor = ...Glow` and `.copy(alpha = ...)` are the
# intended uses and must keep passing.
violations=$(grep -rnE '(color|tint)[[:space:]]*=[[:space:]]*[A-Za-z.]*palette\.[a-zA-Z]*Glow' \
    --include='*.kt' "$SRC_DIR" || true)

if [ -n "$violations" ]; then
    echo "Glow-as-foreground check failed - a *Glow palette color used as text/icon color:"
    echo ""
    echo "$violations"
    echo ""
    echo "Glows are ~50%-alpha fills for halos and shadow spot colors. For a colored foreground use"
    echo "routeGreen/routeAmber/routeRed or accentText - see the doc on UaPalette.azureGlow."
    exit 1
fi

echo "Glow-as-foreground check passed."
