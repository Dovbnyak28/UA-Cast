# Channel search optimization — 2026-10-03

## Scope and evidence

Optimized whole-playlist phone search, not the player, parser limits, network stack or UI layout.
Execution path: `ui/channels/GroupsOverview.kt` →
`data/playlist/PlaylistChannelSearch.kt` → `playlist/ChannelSearch.kt`.
Search already runs on the bounded playlist CPU worker, outside composition. That worker is also
used for playlist parsing, so unnecessary search work delays other playlist operations.

The previous allocation-free matcher restarted at every source position and rechecked the same
prefix. An adversarial title consisting of 8,192 `a` characters followed by `c`, searched with
2,048 `a` characters followed by `b`, therefore required O(name length × query length) comparisons.
This is a CPU performance issue, not evidence of an ANR on the main thread.

Before changing production code, 34 focused tests were run. The existing behavior and the new
equivalence tests passed; the new linear-work budget failed with 26,649 cancellation checkpoints
for two titles. Timings were recorded before the change, not estimated.

## Implementation

`playlist/NormalizedChannelQuery.kt` compiles one KMP prefix table per search. Matching scans
each title once, with amortized O(name length) work; compiling the query takes O(query length).
Extra memory is one O(query length) integer array, independent of channel count. No normalized
name strings, per-channel index, global cache, context, coroutine scope or listener were added.

Policies retained:

- Group/channel order, `tvgName` fallback and the 200-result cap, including `TooBroad` only when
  an additional match actually exists.
- Query trimming, ASCII-regex whitespace normalization and String lowercasing.
- Source whitespace collapse using `Char.isWhitespace`, and per-character `Char.lowercaseChar`.
  Existing Unicode edge cases were deliberately retained, not silently changed.
- Cancellation between channel batches and inside long names, including whitespace runs.
  Added cancellation during prefix compilation and long fallback chains.
- Matching progress is local to each name; only the immutable prefix table is shared.
- Follow-up on 2026-10-04: names shorter than the normalized query are rejected without a
  character scan. Source normalization only collapses/folds characters; it cannot increase length.

An initial post-change check exposed unnecessarily frequent cancellation probes during repeated
one-step fallbacks. Probe frequency was corrected and verified before the successful focused run.
No test thresholds, lint rules, Detekt baseline or screenshot goldens were relaxed.

## Measurements

Local Windows/JDK 21 unit-test JVM; three warmup runs, median of seven measured runs, same fixtures
before/after. No concurrent full regression gate was running during these focused measurements.
The ordinary fixture contains 100,000 synthetic channels as a stress dataset, not a change to
the M3U parser's supported channel limit.

| Scenario | Before | After |
| --- | ---: | ---: |
| 100k ordinary channels, no match | 11.172 ms | 10.021 ms |
| 100k ordinary channels, match at the end | 14.710 ms | 10.077 ms |
| Ordinary channels, stop at the result cap | 0.054 ms | 0.056 ms |
| 16 adversarial repeated-prefix titles | 978.470 ms | 0.813 ms |

These are observations from one local run, not Android device benchmarks or a promised app-wide
speedup. Small timings are sensitive to JIT/scheduling noise; the capped case's 0.002 ms change
is not a meaningful measurement of a regression. The substantial result is removal of quadratic
repeated-prefix work. The after column uses the final focused run on 2026-10-04.

Separately, the follow-up length guard reduced a 4,097-character missing query across 100k short
names from 10.853 ms to 1.144 ms (KMP before vs KMP with the guard, same fixture). The guard's
test initially failed with eight source-scan checkpoints, then passed with zero. The repeated-prefix
work-budget case dropped from 26,649 checkpoints before KMP to 28 in the final implementation.

Raw before reports are retained in the ignored build directory:
`app/build/channel-search-optimization-20261003/before-performance.xml` and
`before-equivalence.xml`. Later focused/full-gate reports are archived alongside them.

## Regression coverage

Ten new tests across three files:

- `ChannelSearchEquivalenceTest`: 4,000 deterministic randomized cases against an allocating
  reference; overlapping prefixes, multiword whitespace and Unicode folding edge cases.
- `ChannelSearchPerformanceTest`: deterministic repeated-prefix work budget and local timing
  observations. Timing ratios are not CI assertions.
- `NormalizedChannelQueryTest`: cancellation during compilation, cancellation inside fallback
  chains whose prefix lengths skip multiples of 1,024, independent progress across names, and
  skipping scans that cannot match because of length.

All 38 focused tests and Detekt passed after the final implementation. The incomplete overall run
from 2026-10-03 is not counted as a successful gate. The replacement run completed on 2026-10-04.
Real phone/TV performance has not been measured in this stage.

## Final regression gate — 2026-10-04

`BUILD SUCCESSFUL in 8m 48s`; 173 tasks, 33 executed and 140 up-to-date. Command:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:detekt `
  :core:test :core:detekt :app:verifyRoborazziDebug `
  :app:testReleaseUnitTest :app:testPlayUnitTest `
  :app:lintDebug :app:lintRelease :app:lintPlay `
  --offline --max-workers=1 --no-daemon --continue
```

| Check | Result |
| --- | --- |
| Debug unit | 2,560 tests / 431 suites; zero failures, errors or skips |
| Release unit | 2,235 tests / 361 suites; zero failures, errors or skips |
| Play unit | 2,235 tests / 361 suites; zero failures, errors or skips |
| Core unit | Valid unchanged cached result: 126 tests / 15 suites; zero failures, errors or skips |
| UI screenshot verification | 47 unchanged; zero changed, added or recorded goldens |
| App/Core Detekt | Passed; no new suppressions or baseline entries |
| Debug/Release/Play lint | Zero errors/warnings; the existing `OldTargetApi` informational hint remains |
| Repository check scripts | All 15 top-level `scripts/check-*.sh` checks passed on the final source |
| Diff whitespace check | Passed |

The full gate log is retained at
`app/build/channel-search-optimization-20261003/full-gate-20261004.log`.
Focused reports for the final implementation are `after-final-performance.xml` and
`after-final-short-name-test.xml` in the same ignored build directory.

Debug and instrumented test APKs compiled successfully. Instrumented tests were not run on a
physical device in this stage. No version bump, signed release APK rebuild, device installation
or GitHub publication was performed. This verifies the scoped optimization; it is not a claim
that every possible application bug has been eliminated.
