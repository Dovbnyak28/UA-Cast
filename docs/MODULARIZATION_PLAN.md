# Modularization plan

A proposal, not a commitment: splitting the single `:app` module is a large, risky refactor with no
functional payoff on its own (build-time/incremental-build wins only), so it should stay a plan
until there's a concrete reason to spend the time (build times becoming painful, or a second app
target that wants to reuse `:core`/`:data`). Nothing in this document has been executed.

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

1. **`cast` <-> `player` cycle** (blocks `:feature-cast`/`:feature-player` being separate modules
   at all, in either dependency direction):
   - `cast/CastContentType.kt` imports `player.StreamMimeClassifier`/`player.StreamType` (cast
     needs player's codec/container classification to build a Cast `MediaInfo`).
   - `player/PlayerModels.kt` and `player/PlayerViewModel.kt` import from `cast` (the player needs
     `CastPlaybackState`/`CastSessionRepository` to react to an active cast session).
   - Fix: extract the shared piece (`StreamMimeClassifier`/`StreamType`, which is pure
     classification with no player-runtime dependency) into `:data` or `:core`, so both `cast` and
     `player` depend downward on it instead of on each other.

2. **`data/prefs/AppPreferences.kt` imports `ui.theme.AppTheme`** - a `:data` file depending on
   `:core`'s design-system enum for a single stored preference value. Fix: either move `AppTheme`
   itself down into `:core`'s non-UI layer (it's just an enum, no Compose dependency) so this
   becomes a legitimate `:data -> :core` edge, or store the preference as a plain string/id in
   `:data` and let the `:app`/UI layer map it to `AppTheme`.

3. **`core/i18n/LocalizedContext.kt` imports `data.prefs.AppPreferences`** - `:core` is meant to be
   the foundation everything else depends on, but this file reads the user's saved language
   preference directly from `:data`. Fix: pass the resolved `AppLanguage` in as a parameter from the
   call site instead of reading prefs from inside `:core`, or move language storage itself into
   `:core` (it's a strong candidate either way, being needed before most of `:data` is relevant).

4. **`data/cast/ProxyServer.kt` imports `diagnostics.CastRouteKind`** (introduced by the routing
   effectiveness counters) - a `:data` file depending on an `:app`-layer type for one callback
   parameter. Fix: move `CastRouteKind`/`CastRouteOutcome` (currently in `diagnostics/`) down into
   `:data` alongside `RemuxEffectivenessStore`, since they're really cast-routing vocabulary, not
   report-formatting vocabulary - `diagnostics/DiagnosticsReportBuilder` would then depend on
   `:data` for them like everything else there already does.

5. **`diagnostics/DiagnosticsReportBuilder.kt` imports `ui.theme.AppTheme`** - same root cause as
   #2 (the report includes the current theme by name); resolved the same way once `AppTheme` moves.

6. **`player/PlayerViewModel.kt` and `cast/CastProxyService.kt` import `MainActivity`** (both for
   a `PendingIntent`/notification tap target back into the app) - a feature module reaching into
   `:app`'s root activity is backwards. Fix: pass the target `Class<*>`/`PendingIntent` in from
   `:app` at construction time instead of each feature hardcoding `MainActivity::class`.

## Migration order

Do this one module at a time, each its own PR, green build gate after each step - never split
everything at once:

1. `:core` first (smallest, fewest inbound edges once #3 above is fixed) - it only needs #3
   resolved before extraction.
2. `:data` next, after #2 and #4 are resolved - everything else already depends downward on it
   correctly.
3. `:feature-player` and `:feature-cast` together (since #1 requires touching both), after the
   `StreamMimeClassifier`/`StreamType` extraction and #6.
4. `:app` last - by this point it's whatever doesn't fit anywhere else, and the module boundary is
   just "what's left," not a design decision.

## Non-goals

- No change to package names or public API shape as part of this - a module boundary should not
  force an unrelated rename.
- No attempt to make `:data`/`:core` reusable outside this app (no second app target exists) - the
  split is justified by build-time isolation and dependency clarity, not by actual reuse.
