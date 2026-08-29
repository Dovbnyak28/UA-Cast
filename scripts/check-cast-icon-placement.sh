#!/usr/bin/env bash
set -euo pipefail

# Chromecast belongs beside the live player controls only. Keep the design rule executable so a
# future navigation/menu change cannot quietly reintroduce a cast action into global UI surfaces.
readonly UI_ROOT='app/src/main/kotlin/com/uacastplayer/ui'
matches=$(rg -n --fixed-strings 'AppIcons.CastToTv' "$UI_ROOT" --glob '*.kt' || true)
violations=$(printf '%s\n' "$matches" | grep -vE '[/\\]player[/\\]|[/\\]theme[/\\]AppIcons\.kt' || true)
if [[ -n "$violations" ]]; then
  printf '%s\n' "$violations" >&2
  echo 'check-cast-icon-placement: cast icon escaped ui/player' >&2
  exit 1
fi

echo 'check-cast-icon-placement: OK'
