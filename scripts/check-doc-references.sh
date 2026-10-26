#!/usr/bin/env bash
set -euo pipefail

# Keep documentation and source comments aligned with the package moves. Generated baseline
# profiles intentionally retain a few historical symbols for runtime warm-up safety, so they are
# excluded from this check.
# Optional roots make the failure boundary testable without altering production documentation.
if [ "$#" -gt 0 ]; then
  readonly SEARCH_ROOTS=("$@")
else
  readonly SEARCH_ROOTS=(docs README.md app/src/main/kotlin core/src/main/kotlin app/src/main/AndroidManifest.xml)
fi
readonly LEGACY_REFERENCES=(
  '(^|[^/[:alnum:]_])cast/TsProgramInfoParser'
  '(^|[^/[:alnum:]_])cast/CastCompatibilityPolicy'
  '(^|[^/[:alnum:]_])cast/DiagnosticCachePolicy'
  '(^|[^/[:alnum:]_])cast/IncompatibilityMemoryPolicy'
  '(^|[^/[:alnum:]_])cast/TsSourceKind'
  '(^|[^/[:alnum:]_])core/i18n/LocalizedContext'
)

# Ripgrep is preferred locally, but the GitHub Ubuntu runner does not guarantee it is installed.
# Keep the same no-match/error exit semantics with the ubiquitous grep fallback.
if command -v rg >/dev/null 2>&1; then
  search_reference() {
    rg -n "$1" "${SEARCH_ROOTS[@]}" --glob '!baseline-prof.txt' --glob '!build/**'
  }
elif command -v grep >/dev/null 2>&1; then
  search_reference() {
    grep -nRE --exclude=baseline-prof.txt --exclude-dir=build -- "$1" "${SEARCH_ROOTS[@]}"
  }
else
  echo 'check-doc-references: neither rg nor grep is available' >&2
  exit 1
fi

for reference in "${LEGACY_REFERENCES[@]}"; do
  if search_reference "$reference"; then
    echo "check-doc-references: stale path found: $reference" >&2
    exit 1
  else
    status=$?
    if [ "$status" -ne 1 ]; then
      echo "check-doc-references: search failed (status $status)" >&2
      exit 1
    fi
  fi
done

echo "check-doc-references: OK"
