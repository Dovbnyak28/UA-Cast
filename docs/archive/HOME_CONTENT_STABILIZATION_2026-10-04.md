# Home content and last-watched key stabilization — 2026-10-04

## Verified problems

### Discarded fallback keys during whole-playlist lookup

`HomeContentPolicy.resolve` called `FavoriteKey.of` for each visited channel. Without `tvg-id`,
this computes a SHA-256 of the stream URL and creates the `name:hash` key, even when the name alone
already rules out a match. The last-watched branch of `PrefetchSelectionPolicy.select` did the same.

The allocation-budget test failed before the change: resolving the last entry of 100,000 channels
without IDs allocated **47,200,208 bytes** in one warmed host-JVM call. This is cumulative temporary
allocation, not a measured live-heap peak, and the lookup was already off Main.

### Cancelled Home lookup kept the shared CPU lane occupied

Execution path: Home `produceState` → bounded playlist CPU dispatcher →
`HomeContentPolicy.resolve` → full channel scan. Cancelling `produceState` prevents stale result
delivery through `withContext`, but does not itself stop a synchronous scan. The cancelled request
continued reading all 100,000 entries before the replacement could use that CPU lane.

The regression test held the first read, cancelled the owner, then released the read. Before the
fix it recorded **100,000 reads**, failing the bounded-work assertion. The stale-result assertion
already passed before the fix: this is confirmed wasted CPU/retirement delay, not proof that the
old UI published a cancelled result.

Before production fixes, 29 focused tests ran: these two tests failed and the other 27 passed.
For this measurement, Home's existing CPU hop was first extracted unchanged into the small
`data/playlist/PlaylistHomeContent.kt` helper and the UI was wired to it. This lets the lifecycle
test exercise the actual production entry point, not a duplicate of the Compose wiring.

## Changes and invariants

- `FavoriteKey.matches(channel, key)` rejects impossible fallback names before hashing. A
  nonblank `tvg-id` still compares directly; a possible fallback still requires exact equality
  with `FavoriteKey.of(channel)`. The SHA-256 format and all persisted keys are unchanged.
- No split-on-colon, case folding, name normalization, digest-length assumption, key migration,
  global cache or stored channel index was introduced. Empty names, blank IDs and explicit IDs
  that resemble fallback keys retain their original identity behavior.
- Home uses `withPlaylistCpuCancellable`; its pure policy checks cancellation initially and
  every 256 channel visits. The coroutine remains owned by `produceState`; no new scope/thread
  is created. The shared dispatcher was reused without changing its ownership.
- The last-watched icon-selection scan reuses the same matcher. Favorite-set lookup, duplicate
  selection keys, priority ordering, lazy evaluation, result cap and cache filtering are unchanged.
- Home's first-match behavior and ten-favorite preview cap remain unchanged. No UI layout,
  player, cast, billing, backup data or icon sources were changed.

## Measurements

Windows/JDK 21 focused unit-test runs, pre-created synthetic playlist of 100,000 channels without
IDs, three warmups and median of seven timed calls. Allocation is measured with the existing
`testsupport/JvmAllocations` helper around one warmed synchronous lookup. The synthetic size is
a stress fixture, not an increase to the parser's supported channel limit.

| Scenario | Before | After |
| --- | ---: | ---: |
| Home: last matching entry | 10.945 ms | 0.555 ms |
| Home: stale key, full scan | 10.115 ms | 0.388 ms |
| Prefetch: last-watched entry, no favorites | 10.855 ms | 0.831 ms |
| Home: last-match temporary allocations | 47,200,208 bytes | 680 bytes |
| Cancelled Home request, observed channel reads | 100,000 | 1 |

The cancellation budget is at most 257 reads after the test's first-read barrier, not a promise
that every cancellation always stops after one read. Timings are local observations; there are
no speedup-ratio assertions in CI and no Android device timing/heap claim.

Raw pre/post focused XML reports are retained under the ignored build directory
`app/build/home-content-polish-20261004/`. The allocation budget of 1 MB and cancellation budget
were set before the fix and were not loosened. No lint/Detekt baseline or goldens were relaxed.

## Regression coverage

Fourteen new tests:

- `HomeContentEquivalenceTest` (4): 1,000 deterministic randomized resolutions against persisted
  keys, first duplicate ID, fallback-shaped explicit IDs and shared URLs with different names.
- `HomeContentPerformanceTest` (2): allocation regression budget and three timing scenarios.
- `HomeContentResolutionCancellationTest` (1): real CPU helper retirement, no stale publication,
  bounded reads and successful replacement lookup.
- `HomeContentCancellationTest` (2): cancellation with no saved key and between channel batches.
- `FavoriteKeyMatchingTest` (3): 2,000 randomized channels × seven key variants against the original
  key function, exact URL/name identity and explicit-ID precedence.
- Two `PrefetchSelectionPolicyTest` additions: fallback-key identity and cancelled last-watched scan.

All 36 focused tests and Detekt passed after the final source changes. Physical phone/TV tests
have not been run in this stage.

## Final gate

`BUILD SUCCESSFUL in 9m 21s`; 173 tasks, 37 executed and 136 up-to-date.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:detekt `
  :core:test :core:detekt :app:verifyRoborazziDebug `
  :app:testReleaseUnitTest :app:testPlayUnitTest `
  :app:lintDebug :app:lintRelease :app:lintPlay `
  --offline --max-workers=1 --no-daemon --continue
```

| Check | Result |
| --- | --- |
| Debug unit | 2,574 tests / 436 suites; zero failures, errors or skips |
| Release unit | 2,249 tests / 366 suites; zero failures, errors or skips |
| Play unit | 2,249 tests / 366 suites; zero failures, errors or skips |
| Core unit | Valid unchanged cached result: 126 tests / 15 suites; zero failures, errors or skips |
| UI screenshot verification | 47 unchanged; zero changed/added/recorded goldens |
| App/Core Detekt | Passed; no suppressions or baseline changes |
| Debug/Release/Play lint | Zero errors/warnings; one existing `OldTargetApi` informational hint per variant |
| Repository gates | All 15 top-level `scripts/check-*.sh` checks passed |
| Diff whitespace check | Passed |

Full output is retained at `app/build/home-content-polish-20261004/full-gate.log`.
Debug and instrumented test APKs compiled; instrumented tests were not run on a physical device.
No signed release APK rebuild, version bump, device install, GitHub publication or computer
shutdown was performed. These results verify the scoped changes, not the absence of every
possible application bug.
