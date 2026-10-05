#!/usr/bin/env bash
# Verify that the Play build cannot expose the sideload updater or its restricted permissions.
#
# Run after :app:bundlePlay (or processPlayMainManifest + generatePlayBuildConfig). Inspecting the
# merged output rather than only src/play/AndroidManifest.xml catches a future library/source-set
# merge that silently puts one of these declarations back into the shipping Play artifact.

set -euo pipefail

MANIFEST="${1:-app/build/intermediates/merged_manifest/play/processPlayMainManifest/AndroidManifest.xml}"
BUILD_CONFIG="${2:-app/build/generated/source/buildConfig/play/com/uacastplayer/BuildConfig.java}"

if [ ! -f "$MANIFEST" ]; then
    echo "check-play-distribution: merged Play manifest not found: $MANIFEST" >&2
    echo "Run :app:bundlePlay first." >&2
    exit 1
fi

if [ ! -f "$BUILD_CONFIG" ]; then
    echo "check-play-distribution: Play BuildConfig not found: $BUILD_CONFIG" >&2
    echo "Run :app:bundlePlay first." >&2
    exit 1
fi

for forbidden in \
    android.permission.REQUEST_INSTALL_PACKAGES \
    android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION \
    UpdateInstallReceiver; do
    if grep -F "$forbidden" "$MANIFEST" >/dev/null; then
        echo "check-play-distribution: forbidden Play manifest entry present: $forbidden" >&2
        exit 1
    fi
done

if ! grep -E 'SELF_UPDATER_ENABLED[[:space:]]*=[[:space:]]*false;' "$BUILD_CONFIG" >/dev/null; then
    echo "check-play-distribution: Play BuildConfig must set SELF_UPDATER_ENABLED=false" >&2
    exit 1
fi

echo "check-play-distribution: OK - updater code and restricted install permissions are absent"
