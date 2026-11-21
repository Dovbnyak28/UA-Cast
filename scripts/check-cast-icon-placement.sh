#!/usr/bin/env bash
set -euo pipefail

# Chromecast belongs beside the live player controls only. Keep the design rule executable so a
# future navigation/menu change cannot quietly reintroduce a cast action into global UI surfaces.
readonly UI_ROOT="${1:-app/src/main/kotlin/com/uacastplayer/ui}"
if ! command -v grep >/dev/null 2>&1 || [[ ! -d "$UI_ROOT" ]]; then
  echo 'check-cast-icon-placement: grep and an existing UI source directory are required' >&2
  exit 1
fi

status=0
matches=$(grep -RHnE --include='*.kt' '\b(PlayerCastButton|CastButton)[[:space:]]*\(' "$UI_ROOT") || status=$?
if [[ "$status" -gt 1 ]]; then
  echo 'check-cast-icon-placement: source scan failed' >&2
  exit 1
fi
status=0
violations=$(printf '%s\n' "$matches" | grep -vE '[/\\]player[/\\]|[/\\]cast[/\\]') || status=$?
if [[ "$status" -gt 1 ]]; then
  echo 'check-cast-icon-placement: exclusion scan failed' >&2
  exit 1
fi
if [[ -n "$violations" ]]; then
  printf '%s\n' "$violations" >&2
  echo 'check-cast-icon-placement: cast icon escaped ui/player' >&2
  exit 1
fi

echo 'check-cast-icon-placement: OK'
