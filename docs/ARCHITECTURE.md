# Architecture boundaries

## Production modules

`:app` depends on `:core`. The dependency never points back.

- `:core` is a Kotlin/JVM module: 23 source files containing shared settings values, navigation
  reductions, MIME/codec policies, bounded IO, JSON, language and security utilities. Production
  dependencies are Kotlin/JDK only. It neither applies an Android plugin nor sees app classes on
  its compile classpath. JVM target is 17, matching the app; the build/test toolchain is JDK 21.
- `:app` owns Android, Compose, Media3, Google Cast, DLNA, network and storage adapters, and app
  composition. Android/network helpers still located under its `core` package are **not** part of
  the pure Gradle module. Package name alone is not a claim of platform independence.
- `:baselineprofile` is a separate test/measurement module, not a production feature layer.

Package/class names of the extracted core sources are unchanged. Persistence keys, wire formats,
profile symbols and callers did not need a migration. `Hex.encode` is now a public core API because
app-side signature/license adapters legitimately consume it; its algorithm is unchanged.

The cross-layer `LicenseRecordCodecTest` stays in app tests because it verifies the contract with
the premium feature's `LicenseTier`. Pure core tests run in `:core:test`, without Robolectric.

## Runtime ownership

| Owner | State/resources it owns | Inputs and outputs | End of lifetime |
| --- | --- | --- | --- |
| `PlayerViewModel` | One Media3 player/media session, UI projection, platform/receiver coordination | Adapts framework observations and executes playback effects | Playback close frees media; ViewModel clear releases player/session |
| `PlayerSessionStateMachine` | Current channel/history and recovery budgets | Pure decisions, no SDK calls | Channel change/retry/session release resets relevant budgets |
| `PlayerStallRecovery` | Exactly one sampler and one delayed silent-stall action, operation generation | Fresh immutable observation → state-machine decision → effect callback | Stop cancels both jobs; pause/handoff/channel change invalidates action; scope cancellation is a second safety net |
| `CastSessionRepository` | SDK session/callback identity, receiver-load generation, exposed Cast state and command routing | SDK events → reducers → effects; delegates resource and recovery lifetimes | Matching SDK disconnect invalidates loads/callbacks and stops owned operations |
| `CastRecoveryRuntime` | Retry budget/PLAYING window via `CastRecoveryEpisode`, pending reload job and generation | Accepted receiver status → bounded decision; reload only if channel metadata and generation still match | Suspension cancels action but keeps budget; new channel/session end resets episode |
| `CastProxySession` | ProxyServer, session token, active resource, playback-attempt ID, foreground-service lifetime | Prepare returns a resource URL or failure; exposes byte/route observations | Failed preparation or stop closes socket/resources and removes the Cast service owner; repeated stop is safe |
| `CastRouteHistory` | Whether this episode played, abandoned direct-route candidate | Accepted status + current route → deduplicated diagnostics/proven compatibility record | Channel/session reset; direct failure alone does not persist a proxy-only verdict |

Recovery components receive the existing Main-bound owner scope. They do not create a singleton,
GlobalScope, second StateFlow, Activity reference or another independent player. The SDK adapter
still owns its direct-probe/suspension integration and delegates existing playback watchdogs to
`CastPlaybackWatchdogs`; extracting every function is not the goal.

The meaningful separation is lifecycle/testability: `PlayerStallRecovery`, `CastRecoveryRuntime`
and `CastRouteHistory` run in deterministic JVM tests without Media3 or GMS. The proxy owner has
a separate Robolectric/real-loopback-socket test proving session reuse and shutdown.

## Enforcement

- `:core:compileKotlin` enforces the actual module classpath; app/framework classes are unavailable.
- Detekt uses the same checked-in configuration and empty baseline in both modules. The old
  `CastSessionRepository` LargeClass suppression has been removed; its class must pass the real rule.
- `scripts/check-architecture-boundaries.sh` checks app dependency direction, pure-module imports,
  SDK/UI-free recovery owners, and rejects direct ProxyServer references from the Cast repository.
- Locale/documentation checks scan both production source roots.
- Normal CI and signed-release gates run core tests/detekt alongside app tests and quality checks.
  Normal CI also uploads both modules' reports. Workflow edits are not a claim that remote CI ran.

Local verification:

```powershell
.\gradlew.bat :core:test :core:detekt :app:testDebugUnitTest :app:detekt :app:lintPlay :app:assembleDebug :app:assembleDebugAndroidTest :app:bundlePlay --continue --offline --no-daemon --console=plain
```

Do not use `:app:testDebugUnitTest` alone as the complete project test gate: pure tests now belong
to `:core:test`. Individual package policies can still be exercised independently there.

## Review after extraction

No UI acquired subsystem logic; no new global or duplicated application state was introduced.
Mutable state moved with its cancellation/reset rules. New components own present responsibilities,
not speculative replacements. Framework creation stays in app and core does not import features.

This addresses the single-production-module boundary and the selected mixed-ownership hotspots.
It is not a claim that all large files are inherently wrong or that architecture is now final.
AppViewModel, remaining SDK orchestration and large UI compositions still deserve targeted review
when their behavior changes. Full feature-by-feature module splitting is optional subsequent work,
not a prerequisite to keeping these boundaries enforceable.

## Verified result (2026-09-05)

- Core: 114 tests in 13 files; app: 1,909 tests. Total: 2,023, no failures/errors/skips.
  The 12 new regressions supplement, rather than replace, the previous 2,011 cases.
- Both detekt tasks pass with zero findings and the original empty baseline.
- Architecture/import, locale and documentation gates pass; APK and R8 Play AAB build successfully.
- Mi A2 API 30: 13/13 playback lifecycle/video-fit tests pass with debug files/preferences restored.
- Nonblank lines measured before/after this step: PlayerViewModel 761 → 704;
  CastSessionRepository 891 → 760. Comments are included in these counts, so they are not detekt's
  metric. The important change is ownership and independently exercised behavior, not this count.
- No real receiver compatibility or Google Play purchase result is claimed by this refactoring.
