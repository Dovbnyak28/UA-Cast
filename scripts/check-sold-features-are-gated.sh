#!/usr/bin/env bash
# Fails the build if the premium screen offers to sell a feature that nothing actually gates.
#
# The two halves of a paywall live in different files and neither knows about the other:
# PremiumLabels.SOLD decides what is advertised, and a `gate.guard(Feature.X)` call site decides
# what is withheld. Nothing made them agree, and they did not: Feature.RAW_TS_REMUX was listed on
# the premium screen, with a name and a lock badge, while every free user had it. That is the worst
# shape this can take - it costs a sale *and* it is the app telling the user something untrue on the
# one screen where it is asking to be trusted with money - and it produced no error anywhere.
#
# The check runs the direction that matters. A gated feature missing from SOLD is merely invisible;
# a sold feature missing a gate is a lie.
#
# Usage: scripts/check-sold-features-are-gated.sh (run from the repo root)

set -euo pipefail

LABELS="app/src/main/kotlin/com/uacastplayer/ui/premium/PremiumLabels.kt"
UI_SOURCES="app/src/main/kotlin/com/uacastplayer"

if [ ! -f "$LABELS" ]; then
    echo "check-sold-features-are-gated: $LABELS not found" >&2
    exit 1
fi

# Everything between "val SOLD" and the closing paren of its listOf(...).
sold=$(sed -n '/val SOLD/,/^    )/p' "$LABELS" | grep -oE 'Feature\.[A-Z_]+' | sed 's/Feature\.//' | sort -u)

if [ -z "$sold" ]; then
    echo "check-sold-features-are-gated: could not read PremiumLabels.SOLD - has it been renamed?" >&2
    exit 1
fi

# Any call site that actually consults the gate for a named feature.
gated=$(grep -rhoE '(guard|isLocked)\(\s*Feature\.[A-Z_]+' "$UI_SOURCES" --include='*.kt' \
    | grep -oE 'Feature\.[A-Z_]+' | sed 's/Feature\.//' | sort -u || true)

ungated=$(comm -23 <(echo "$sold") <(echo "$gated"))

if [ -n "$ungated" ]; then
    echo "check-sold-features-are-gated: these are advertised on the premium screen but gated nowhere:" >&2
    echo "$ungated" | sed 's/^/  Feature./' >&2
    echo >&2
    echo "Either add a gate.guard(Feature.X) { ... } at the offer point, or - if the feature should" >&2
    echo "not be sold at all - take it out of PremiumLabels.SOLD and put it in" >&2
    echo "FeaturePolicy.FREE_FEATURES, so the two tables agree." >&2
    exit 1
fi

count=$(echo "$sold" | wc -l | tr -d ' ')
echo "check-sold-features-are-gated: OK - all $count sold feature(s) are gated"
