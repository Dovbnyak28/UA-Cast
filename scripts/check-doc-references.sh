#!/usr/bin/env bash
set -euo pipefail

# Keep documentation and source comments aligned with the package moves. Generated baseline
# profiles intentionally retain a few historical symbols for runtime warm-up safety, so they are
# excluded from this check.
readonly SEARCH_ROOTS=(docs README.md app/src/main/kotlin app/src/main/AndroidManifest.xml)
readonly LEGACY_REFERENCES=(
  '(^|[^/[:alnum:]_])cast/TsProgramInfoParser'
  '(^|[^/[:alnum:]_])cast/CastCompatibilityPolicy'
  '(^|[^/[:alnum:]_])cast/DiagnosticCachePolicy'
  '(^|[^/[:alnum:]_])cast/IncompatibilityMemoryPolicy'
  '(^|[^/[:alnum:]_])cast/TsSourceKind'
  '(^|[^/[:alnum:]_])core/i18n/LocalizedContext'
)

for reference in "${LEGACY_REFERENCES[@]}"; do
  if rg -n "$reference" "${SEARCH_ROOTS[@]}" \
    --glob '!baseline-prof.txt' --glob '!build/**'; then
    echo "check-doc-references: stale path found: $reference" >&2
    exit 1
  fi
done

echo "check-doc-references: OK"
