#!/usr/bin/env bash
# Enforces the package dependency direction documented by the architecture audit. This checks
# production imports only: tests may deliberately reach across a boundary to exercise an adapter.
#
# Usage: scripts/check-architecture-boundaries.sh [source-root] (run from the repo root). The
# optional source root exists so the checker itself can be exercised against a small fixture.

set -euo pipefail

SRC_ROOT="${1:-app/src/main/kotlin/com/uacastplayer}"

if [ ! -d "$SRC_ROOT" ]; then
    echo "check-architecture-boundaries: $SRC_ROOT does not exist" >&2
    exit 1
fi

failed=0

report_imports() {
    local message="$1"
    local matches="$2"
    if [ -n "$matches" ]; then
        echo "$message" >&2
        echo "$matches" >&2
        echo >&2
        failed=1
    fi
}

data_to_ui=$(grep -RHn --include='*.kt' \
    '^import com\.uacastplayer\.ui\.' "$SRC_ROOT/data" || true)
report_imports "data must not import ui:" "$data_to_ui"

core_to_app_or_data=$(grep -RHnE --include='*.kt' \
    '^import com\.uacastplayer\.(app|data)\.' "$SRC_ROOT/core" || true)
report_imports "core must not import app or data:" "$core_to_app_or_data"

player_to_cast=$(grep -RHn --include='*.kt' \
    '^import com\.uacastplayer\.cast\.' "$SRC_ROOT/player" "$SRC_ROOT/ui/player" || true)
report_imports "player feature must use PlayerCastPort instead of importing cast:" "$player_to_cast"

cast_to_player=$(grep -RHn --include='*.kt' \
    '^import com\.uacastplayer\.player\.' "$SRC_ROOT/cast" || true)
report_imports "cast feature must not import player:" "$cast_to_player"

ui_to_proxy_server=$(grep -RHn --include='*.kt' \
    'com\.uacastplayer\.data\.cast\.ProxyServer' "$SRC_ROOT/ui" || true)
report_imports "UI must not import or directly reference ProxyServer:" "$ui_to_proxy_server"

if [ "$failed" -ne 0 ]; then
    echo "Architecture boundary check failed. Move shared pure concepts downward instead of adding an exception." >&2
    exit 1
fi

echo "check-architecture-boundaries: OK"
