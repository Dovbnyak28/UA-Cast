#!/usr/bin/env bash
# Enforces the package dependency direction documented by the architecture audit. This checks
# production imports only: tests may deliberately reach across a boundary to exercise an adapter.
#
# Usage: scripts/check-architecture-boundaries.sh [source-root] (run from the repo root). The
# optional source root exists so the checker itself can be exercised against a small fixture.

set -euo pipefail

if ! command -v grep >/dev/null 2>&1; then
    echo "check-architecture-boundaries: required command grep is unavailable" >&2
    exit 2
fi

# grep returns 1 for no matches, but 2 for a broken check (permissions, missing directory, etc.).
# Never turn a failed scan into a successful architecture gate.
scan_imports() {
    local status=0
    grep "$@" || status=$?
    if [ "$status" -gt 1 ]; then
        return "$status"
    fi
}

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

data_to_ui=$(scan_imports -RHn --include='*.kt' \
    '^import com\.uacastplayer\.ui\.' "$SRC_ROOT/data")
report_imports "data must not import ui:" "$data_to_ui"

core_to_app_or_data=$(scan_imports -RHnE --include='*.kt' \
    '^import com\.uacastplayer\.(app|data)\.' "$SRC_ROOT/core")
report_imports "core must not import app or data:" "$core_to_app_or_data"

# The production JVM module is a real compile-time boundary as well as a package convention.
# A custom source-root fixture exercises only its own tree, never the caller's checkout.
if [ "$#" -eq 0 ]; then
    CORE_ROOT="core/src/main/kotlin/com/uacastplayer/core"
    if [ ! -d "$CORE_ROOT" ]; then
        echo "check-architecture-boundaries: core module source root is missing" >&2
        exit 1
    fi
    core_to_framework=$(scan_imports -RHnE --include='*.kt' \
        '^import (android\.|androidx\.|okhttp3\.|com\.google\.|com\.uacastplayer\.(app|data|ui|cast|player|dlna|premium)\.)' \
        "$CORE_ROOT")
    report_imports "JVM core must not depend on app/features or Android/network frameworks:" "$core_to_framework"
fi

# These runtime owners are deliberately testable without launching either playback SDK.
for owner in "$SRC_ROOT/player/PlayerStallRecovery.kt" "$SRC_ROOT/cast/CastRecoveryRuntime.kt" \
    "$SRC_ROOT/cast/CastRouteHistory.kt"; do
    if [ -f "$owner" ]; then
        runtime_to_framework=$(scan_imports -HnE '^import (android\.|androidx\.|com\.google\.|com\.uacastplayer\.ui\.)' "$owner")
        report_imports "Recovery runtime must not depend on UI/Media3/Cast SDK:" "$runtime_to_framework"
    fi
done

if [ -f "$SRC_ROOT/cast/CastSessionRepository.kt" ]; then
    cast_to_proxy=$(scan_imports -Hn 'com\.uacastplayer\.data\.cast\.ProxyServer' "$SRC_ROOT/cast/CastSessionRepository.kt")
    report_imports "Cast SDK adapter must use CastProxySession as its resource owner:" "$cast_to_proxy"
fi

player_to_cast=$(scan_imports -RHn --include='*.kt' \
    '^import com\.uacastplayer\.cast\.' "$SRC_ROOT/player" "$SRC_ROOT/ui/player")
report_imports "player feature must use PlayerCastPort instead of importing cast:" "$player_to_cast"

cast_to_player=$(scan_imports -RHn --include='*.kt' \
    '^import com\.uacastplayer\.player\.' "$SRC_ROOT/cast")
report_imports "cast feature must not import player:" "$cast_to_player"

ui_to_proxy_server=$(scan_imports -RHn --include='*.kt' \
    'com\.uacastplayer\.data\.cast\.ProxyServer' "$SRC_ROOT/ui")
report_imports "UI must not import or directly reference ProxyServer:" "$ui_to_proxy_server"

if [ "$failed" -ne 0 ]; then
    echo "Architecture boundary check failed. Move shared pure concepts downward instead of adding an exception." >&2
    exit 1
fi

echo "check-architecture-boundaries: OK"
