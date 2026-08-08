#!/usr/bin/env bash
# Fails the build if the premium domain grows a dependency on Android, AndroidX, a billing SDK or
# any other part of this app.
#
# "The premium layer does not depend on UI, on Billing or on Google Play" is the requirement the
# whole layer exists to satisfy, and a requirement that lives only in a review comment is one that
# survives exactly until the first hurried afternoon. This turns it into a property: the moment
# com/uacastplayer/premium/ imports android.*, androidx.*, a billing client, or anything from
# another package of this app, the build stops.
#
# The one deliberate exception is kotlinx.coroutines: StateFlow is how access is published to the
# UI, it is pure Kotlin with no Android in it, and the alternative - hand-rolling an observer -
# would be worse code for no architectural gain.
#
# Usage: scripts/check-premium-purity.sh (run from the repo root)

set -euo pipefail

PREMIUM_DIR="app/src/main/kotlin/com/uacastplayer/premium"

if [ ! -d "$PREMIUM_DIR" ]; then
    echo "check-premium-purity: $PREMIUM_DIR does not exist" >&2
    exit 1
fi

# Every import that is not kotlin/kotlinx, and not the premium package talking to itself.
violations=$(grep -RHn --include='*.kt' '^import ' "$PREMIUM_DIR" \
    | grep -v 'import kotlin\.' \
    | grep -v 'import kotlinx\.' \
    | grep -v 'import com\.uacastplayer\.premium\.' \
    || true)

if [ -n "$violations" ]; then
    echo "The premium domain must not depend on Android, on a store SDK, or on the rest of the app." >&2
    echo "Move whatever needs those into data/premium/ (implementation) or ui/premium/ (screens)," >&2
    echo "and leave an interface behind in premium/ for it to implement." >&2
    echo >&2
    echo "$violations" >&2
    exit 1
fi

echo "check-premium-purity: OK - $(find "$PREMIUM_DIR" -name '*.kt' | wc -l | tr -d ' ') files, no outward dependencies"
