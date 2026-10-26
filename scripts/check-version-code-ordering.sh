#!/usr/bin/env bash
# Fails the build if the universal release APK is not the highest versionCode of the four.
#
# A versionCode that goes down is not an update - Android refuses the install outright, and the
# user is told only "App not installed". So the ordering between the per-ABI APKs and the universal
# one decides, permanently and per install, which artifacts a given device can ever move to.
#
# The universal APK has to be the highest, because it is the one that runs on every device and is
# therefore the one every other install must be able to move *to*: from Play, from a per-ABI
# download, from anywhere. A bundle build carries no ABI filter either, so it lands on the same
# code and inherits the same property. The reverse direction - universal to per-ABI - is the one
# this gives up, and it costs nothing, since there is no device the universal APK fails to serve.
#
# This ran as a review comment in app/build.gradle.kts until an off-by-one in an offset map put
# universal below all three per-ABI APKs. Reading it back out of the artifact is the only check
# that cannot be wrong about what was actually built.
#
# Usage: scripts/check-version-code-ordering.sh [path/to/output-metadata.json]
#        Requires :app:assembleRelease to have run first.

set -euo pipefail

METADATA="${1:-app/build/outputs/apk/release/output-metadata.json}"

if [ ! -f "$METADATA" ]; then
    echo "check-version-code-ordering: $METADATA not found - run :app:assembleRelease first" >&2
    exit 1
fi

# One "<name> <versionCode>" line per output. An element with no ABI filter is the universal APK.
python_cmd=()
if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
    python_cmd=(python3)
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
    python_cmd=(python)
elif command -v py >/dev/null 2>&1 && py -3 -c 'import json' >/dev/null 2>&1; then
    python_cmd=(py -3)
fi

if [ "${#python_cmd[@]}" -gt 0 ]; then
    rows=$("${python_cmd[@]}" -c '
import json, sys
elements = json.load(open(sys.argv[1]))["elements"]
for e in elements:
    filters = e.get("filters") or []
    abi = next((f.get("value") for f in filters if f.get("filterType") == "ABI"), "universal")
    print(abi, e["versionCode"])
' "$METADATA")
elif command -v node >/dev/null 2>&1 && node -e 'JSON.parse("{}")' >/dev/null 2>&1; then
    rows=$(node -e '
const fs = require("fs");
const elements = JSON.parse(fs.readFileSync(process.argv[1], "utf8")).elements;
for (const element of elements) {
    const filters = element.filters || [];
    const abiFilter = filters.find(filter => filter.filterType === "ABI");
    console.log(abiFilter ? abiFilter.value : "universal", element.versionCode);
}
' "$METADATA")
else
    echo "check-version-code-ordering: no working JSON runtime found (tried Python 3 and Node.js)" >&2
    exit 1
fi

# A missing split is a release failure too. Checking only the rows that happened to be present made
# a universal-only build report the deeply misleading "outranks all 0 per-ABI APKs". Require the
# exact artifact set configured in app/build.gradle.kts before comparing any codes.
expected_abis=("armeabi-v7a" "arm64-v8a" "x86_64" "universal")
for abi in "${expected_abis[@]}"; do
    matches=$(echo "$rows" | awk -v expected="$abi" '$1 == expected { count++ } END { print count + 0 }')
    if [ "$matches" -ne 1 ]; then
        echo "check-version-code-ordering: expected exactly one $abi APK, found $matches" >&2
        echo "$rows" >&2
        exit 1
    fi
done

count=$(echo "$rows" | wc -l | tr -d ' ')
if [ "$count" -ne "${#expected_abis[@]}" ]; then
    echo "check-version-code-ordering: expected exactly ${#expected_abis[@]} APKs, found $count" >&2
    echo "$rows" >&2
    exit 1
fi

universal=$(echo "$rows" | awk '$1 == "universal" { print $2 }')

violations=$(echo "$rows" | awk -v u="$universal" '$1 != "universal" && $2 >= u { print "  " $1 " = " $2 " >= universal " u }')

if [ -n "$violations" ]; then
    echo "check-version-code-ordering: the universal APK must outrank every per-ABI APK." >&2
    echo "Anyone holding one of these could never install the universal APK over it:" >&2
    echo "$violations" >&2
    echo >&2
    echo "See abiVersionCodeOffsets in app/build.gradle.kts - universal takes the highest offset." >&2
    exit 1
fi

echo "check-version-code-ordering: OK - universal $universal outranks all $((count - 1)) per-ABI APKs"
