#!/usr/bin/env bash
# Verify that the legal documents are shipped in every APK/AAB produced for a variant.
#
# The policy is intentionally kept in the repository root for publishing, but the app's in-app
# screen reads it from Android assets. This check prevents a future source-set change from leaving
# the store page and the installed app with different legal content.

set -euo pipefail

ARTIFACT_DIR="${1:-app/build/outputs/apk/debug}"

if [ ! -d "$ARTIFACT_DIR" ]; then
    echo "check-legal-assets: artifact directory not found: $ARTIFACT_DIR" >&2
    exit 1
fi

if [ ! -f "legal/privacy-policy.html" ] || [ ! -f "legal/terms-of-use.html" ]; then
    echo "check-legal-assets: source legal documents are missing" >&2
    exit 1
fi

mapfile -t artifacts < <(find "$ARTIFACT_DIR" -maxdepth 1 -type f \( -name '*.apk' -o -name '*.aab' \) -print)
if [ "${#artifacts[@]}" -eq 0 ]; then
    echo "check-legal-assets: no APKs or AABs found in $ARTIFACT_DIR" >&2
    exit 1
fi

for artifact in "${artifacts[@]}"; do
    for document in privacy-policy.html terms-of-use.html; do
        # Retry the complete listing check because Windows antivirus can briefly expose a partial
        # ZIP listing while Gradle has just finished writing an artifact.
        found=0
        for attempt in 1 2 3; do
            if command -v unzip >/dev/null 2>&1; then
                listing=$(unzip -Z1 "$artifact" 2>/dev/null || true)
            elif command -v python3 >/dev/null 2>&1; then
                listing=$(python3 - "$artifact" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    print("\n".join(archive.namelist()))
PY
)
            else
                echo "check-legal-assets: neither unzip nor Python 3 is available" >&2
                exit 1
            fi
            # APKs use assets/<file>; AAB base modules use base/assets/<file>.
            # Do not use grep -q here: with pipefail, grep exiting early makes printf receive
            # SIGPIPE on a large AAB listing and turns a real match into a false negative.
            if printf '%s\n' "$listing" | grep -E "(^|/)assets/$document$" >/dev/null; then
                found=1
                break
            fi
            [ "$attempt" -eq 3 ] || sleep 0.2
        done
        if [ "$found" -ne 1 ]; then
            echo "check-legal-assets: $document is missing from $artifact" >&2
            exit 1
        fi
    done
done

echo "check-legal-assets: OK - ${#artifacts[@]} artifact(s) contain both legal documents"
