#!/usr/bin/env bash
# Verify that the Play build does not declare APK-install permissions or an install receiver.
# The self-updater was removed from the app (2026-11-21); this guards against a library or a
# future change bringing those declarations back into the shipping Play artifact.
#
# Run after :app:bundlePlay (or processPlayMainManifest). It inspects the merged output, so a
# permission contributed by a library is caught too.

set -euo pipefail

MANIFEST="${1:-app/build/intermediates/merged_manifest/play/processPlayMainManifest/AndroidManifest.xml}"

if [ ! -f "$MANIFEST" ]; then
    echo "check-play-distribution: merged Play manifest not found: $MANIFEST" >&2
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

echo "check-play-distribution: OK - no APK-install permissions or install receiver in the Play manifest"
