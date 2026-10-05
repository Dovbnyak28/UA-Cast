# Modularization plan

A proposal for the remaining split: the project is still one production `:app` module, but several
pure seams have already been extracted (`PlayerSessionStateMachine`, cast watchdog/recovery
policies, and proxy response serving). The full Gradle split remains a deliberate, risky refactor
with build-time/incremental-build payoff rather than a user-facing feature.

## Proposed modules

| Module | Contents | Depends on |
|---|---|---|
| `:core` | `core/i18n`, `core/io`, `core/net`, `log/`, `ui/theme/` (design system: colors, type, `AppIcons`, motion) | nothing else in this app |
| `:data` | `data/` (playlist, epg, icons, prefs, backup, cache), `playlist/`, `epg/`, `favorites/`, `icons/`, `backup/`, `performance/` | `:core` |
| `:feature-player` | `player/` (ExoPlayer wiring, `PlayerViewModel`), `ui/player/` | `:core`, `:data` |
| `:feature-cast` | `cast/`, `data/cast/` (the proxy: `ProxyServer`, `ProxyHttpServer`, `ProxyResourceRegistry`, `RawTsRemuxSession`), `proxy/` | `:core`, `:data` |
| `:app` | `ui/nav/`, `ui/settings/`, `ui/legal/`, `home/`, `diagnostics/`, `MainActivity`, `AppViewModel`, navigation/composition glue | all of the above |

`diagnostics/` stays in `:app` rather than becoming its own module: it's a thin aggregator that
reads from every other layer (log buffer, prefs, cast routing) to build one report, which is
exactly the shape of a composition-root concern, not a reusable one.

## Cross-package dependencies that block this today

Found by grepping `^import com.uacastplayer\.` per top-level package and keeping only edges that
cross a proposed module boundary in the *wrong* direction (a lower-layer module importing from a
higher one) or that create a cycle. Everything else (e.g. `ui -> playlist`, `data -> log`) already
respects the proposed dependency direction and needs no work.

1. **Resolved:** the player feature now owns a narrow `PlayerCastPort` contract. The app
   composition root connects it to `CastSessionRepository` through `PlayerCastAdapter`; neither
   `player/` nor `ui/player/` imports the Cast feature. Codec display names live beside the pure
   codec models under `core.cast`, so the UI does not need a Cast dependency for formatting.

2. **Resolved:** `data/prefs/AppPreferences.kt` no longer imports the UI theme enum. Keep the
   preference value as a data-layer representation and preserve this boundary in future changes.

3. **Resolved:** the Android locale adapter now lives beside `AppPreferences` under `data/prefs`.
   Pure language matching stays in `core/i18n`, so `:core` does not read settings and the adapter
   can move with `:data` without creating a reverse dependency.

4. **Resolved:** cast routing vocabulary now lives under `core.cast`, so `ProxyServer` does not
   depend on the diagnostics/reporting layer.

5. **Resolved:** the diagnostics report no longer imports the UI theme enum.

6. **Resolved:** player media-session and cast notifications now resolve the launch activity
   through `PackageManager` at runtime. Neither feature package imports `MainActivity`, so the
   future `:feature-player`/`:feature-cast` modules no longer reach upward into `:app` for a
   notification tap target. Keep this lookup in the feature adapter; the composition root should
   not be reintroduced as a dependency.

## Migration order

Do this one module at a time, each its own PR, green build gate after each step - never split
everything at once:

1. `:core` first (smallest); the former locale-preference edge in #3 is already resolved.
2. `:data` next; the former theme and cast-routing edges are already resolved.
3. `:feature-player` and `:feature-cast`; their former direct dependency and `MainActivity`
   reach-through are already removed, and the architecture gate now preserves both boundaries.
4. `:app` last - by this point it's whatever doesn't fit anywhere else, and the module boundary is
   just "what's left," not a design decision.

## Non-goals

- No change to package names or public API shape as part of this - a module boundary should not
  force an unrelated rename.
- No attempt to make `:data`/`:core` reusable outside this app (no second app target exists) - the
  split is justified by build-time isolation and dependency clarity, not by actual reuse.
