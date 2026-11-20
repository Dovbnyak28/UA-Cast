# Live-window playback stabilization — 2026-10-02

## Scope

Follow-up to the user's request to continue polishing after the Mi A2 device checks.
Analysis preceded the regression test and the production change. Existing unrelated changes
in this checkout are preserved. No dependency upgrade, new permission, version increment,
publication or primary-app data replacement is part of this pass.

## Confirmed issue — P2: live-window recovery bypasses playback intent

File: `app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt`.
Function: `handlePlaybackError`, `RecoverLiveWindow` branch.

Execution path:

1. Local HLS playback is paused by the user or by `onEnterBackground(false)`.
2. Media3 reports `ERROR_CODE_BEHIND_LIVE_WINDOW`; the session permits its bounded recovery.
3. The old branch calls `seekToDefaultPosition()` and unconditionally calls `prepare()`.
4. The engine enters `STATE_BUFFERING` even though local loading is not currently wanted.

Other retry paths already use `LocalPlaybackRetryPolicy` to drop a retry after explicit pause
or remote handoff, or defer it after a lifecycle pause until the app returns. The live-window
branch bypassed that policy. This can restart unwanted source loading; the pause remains set,
so this finding is **not** a claim that this branch itself starts audible background playback.

## Before-fix evidence

`PlayerLiveWindowLifecycleTest` drives the real ViewModel and ExoPlayer with a controlled
error delivered to the same listener production registers. `stop()` models the engine's idle
error state; the fixture does not need a provider to change its live manifest at just the
right instant. No new production testing entry point was exposed.

Before changing production code: **7 tests, 4 failures, 0 errors/skips**. Each failed state
assertion expected `STATE_IDLE` (1) but received `STATE_BUFFERING` (2):

- lifecycle-paused/backgrounded recovery;
- explicitly paused recovery;
- a late error delivered under Chromecast ownership;
- a late error delivered under DLNA ownership.

The remote-ownership cases are deterministic adversarial callback tests, not a claim that
this exact callback ordering was observed on a physical receiver. They pin the required
guard against sharing the provider connection with a receiver. All four failures exercise
one implementation gap, not four independent bugs.

Before-fix XML: `app/build/device-audit/stabilization-20261002/pre-fix-live-window.xml`
(local ignored test evidence).

## Minimal fix and preserved behavior

Replace the direct `prepare()` with the existing `performScheduledPlaybackRetry()` call,
after seeking to the default position. The existing policy then:

| Current ownership/intent | Result |
| --- | --- |
| Foreground local playback wanted | prepare immediately at the default position |
| Lifecycle pause while backgrounded | defer prepare/play until foreground |
| Explicit user pause | remain idle until explicit Play |
| Chromecast or DLNA ownership | do not prepare the local source |

No retry limit, debounce, buffer size, codec setting, data source, state-machine structure,
UI layout or receiver protocol changed. Closing playback and sleep expiry continue clearing
deferred recovery through their existing cleanup paths.

## Verification

- All **200 player-package host tests passed**, including the seven new regressions.
- App detekt passed without new suppressions or baseline entries.
- Debug application and native-test APKs built successfully.
- Architecture boundary, detekt-baseline, Play-distribution and sensitive-log checks passed.
- Added a native regression to `PlayerLifecycleInstrumentedTest` for both background deferral
  and explicit-pause behavior, including successful subsequent foreground/explicit playback.

### Physical Mi A2 — Android 11 / API 30

The preserved-device runner installed only `com.uacastplayer.debug` and its test APK. The
primary package was not replaced or cleared. Original debug files/preferences were backed
up, moved aside for fixtures and restored in `finally` after the run.

**OK (33 tests), 130.715s**, no failures:

- player lifecycle: 15, including the new live-window callback regression;
- video-fit UI: 4;
- Activity recreation/parental restoration: 2;
- native MediaSession command contract: 3;
- artwork refresh/ownership: 4;
- remote lifecycle/crypto: 5.

Log: `app/build/device-audit/instrumented-23ea1f911a174aa88326bdbb94ab9b44.txt`.
The recovery archive and preserved fixture data are private local diagnostics, not release assets.
No screen-lock settings or persistent display settings changed.

Installed APK SHA-256:

- Debug: `D10F425407F14C503684FB472DB3D6A7C9BF9014BA708DD32263C8B37E314CD5`.
- Native tests: `9310EA09C45B0911B1E939B6852F337764DDE433F2D03C2B6B44DD968D06C2B7`.

### Full host regression

The first broad run passed all **2,390 debug** and **2,123 release** tests and screenshot
verification without recording new goldens. Play reported one failure among 2,123 tests;
it was investigated rather than ignored or simply retried.

### Test synchronization defect found by the broad run

File: `app/src/test/kotlin/com/uacastplayer/data/cast/ProxyHttpServerTest.kt`.
Failing case: `handler from stopped generation cannot release new generation IP slot`,
at the final immediate `activeClientCountForTesting() <= 8` assertion.

Production rejects an excess connection in this order: increment `rejectedPerIp`, then
`closeClient()` removes its socket from the tracked set. Observing the atomic metric is not
proof that the subsequent removal has executed. The test could observe a ninth socket in
this short interval despite successful rejection. This was a test synchronization defect,
not evidence that a ninth request bypassed authorization or the IP limit.

The test now checks stronger completion conditions:

- eight new-generation **response handlers** must have entered, proving IP admission rather
  than only socket acceptance;
- after stopping the old pool and releasing its blocked handler, the old worker thread must
  exit, proving the outer `releaseIpSlot` cleanup ran before testing the ninth connection;
- the rejected peer must see EOF/reset, and the eight admitted clients remain tracked.

The other per-IP test also waits for tracked-socket removal instead of treating the earlier
metric publication as a completion barrier. The existing bounded waits remain bounded;
there is no fixed sleep to hide the race. Production proxy code, admission limits, timeout
policies and security checks were not changed.

Failure XML: `app/build/device-audit/stabilization-20261002/pre-fix-proxy-test-synchronization.xml`.

### EPG benchmark methodology defect found by the next broad run

After correcting proxy-test synchronization, all debug/release tests and screenshots passed again.
Play exposed a separate timing assertion in `EpgSnapshotSizeTest`: decode took 19,927,200ns versus
21,284,600ns for raw XML parsing (the unchanged 75% ceiling was 15,963,450ns).

Inspection found two concrete mismatches in the comparison:

- XML assigned the same January 2024 start/stop time to every programme, while binary input held
  60 successive slots per channel starting at the Unix epoch. The purportedly equivalent inputs
  were not equivalent. A new fixture-parity regression failed before correcting the generator.
- Binary decode built query-ready `EpgData`, but the XML measurement stopped at a flat SAX result.
  It omitted the guards, retention, grouping/sorting and index construction used by the actual
  `EpgDocumentPipeline` restore path.

The test now derives XML timestamps from the same programme objects and measures the existing
production pipeline with a fixed clock, UTC zone and heap budget. A separate parity assertion
requires both decoded guides to preserve all channels, programmes and truncation flags. Fixture
generation stays outside timed blocks. The 75% ratio, three warmups and five timed runs are
unchanged. Production EPG code was **not** modified to satisfy the benchmark; this is a corrected
test, not a measured improvement in application performance or a guarantee of device latency.

Before-fix evidence (local ignored XML):

- `app/build/device-audit/stabilization-20261002/pre-fix-epg-benchmark.xml`;
- `app/build/device-audit/stabilization-20261002/pre-fix-epg-fixture-parity.xml`.

### Final host checks

The final gate completed the following checks (test-result XML totals, with zero failures,
errors or skipped tests):

| Suite | Tests | Result |
| --- | ---: | --- |
| Debug | 2,391 | passed |
| Release | 2,124 | passed |
| Play | 2,124 | passed |
| Core | 126 | existing passing result reused as up-to-date |

These are per-variant runs; many tests overlap and the counts are not distinct production scenarios.
Both EPG tests pass in all three app variants. Debug screenshot verification passed against
existing goldens. Android Lint passed for Debug, Release and Play, with 0 errors, 0 warnings
and one informational `OldTargetApi` hint for `targetSdk = 36` in each report. Detekt passed
without added baseline entries or suppressions. Architecture, Play-distribution, sensitive-log,
documentation-reference and whitespace checks passed.

Command:

```powershell
./gradlew.bat :core:test :app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest `
  :app:lintDebug :app:lintRelease :app:lintPlay :app:detekt :app:assembleRelease `
  '-Puacast.requireSigning=true' --continue --offline --max-workers=1 --no-daemon
```

The release shrinker emitted two toolchain messages:
`Class file resource provider does not support async parsing:
com.android.builder.dexing.r8.ClassFileProviderFactory$OrderedClassFileResourceProvider`.
They remain visible in build output; no dependency version was changed during this stabilization.

The final Gradle gate finished **BUILD SUCCESSFUL in 12m 31s** (155 tasks). Release/Core test
results were reused where Gradle verified their inputs were unchanged. Debug, Play, screenshot
verification and release shrinking/packaging work completed in this gate.

### Signed universal release APK

- Path: `app/build/outputs/apk/release/app-universal-release.apk`.
- Package: `com.uacastplayer`; version **0.9.7**, universal version code **164**.
- Size: **24,154,856 bytes**.
- Minimum API: 24; target/compile API: 36.
- Native ABIs verified from the packaged APK: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- `apksigner verify --verbose --print-certs`: passed; APK v2 and v3 signatures verified.
- Signer certificate SHA-256:
  `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.
- APK SHA-256:
  `2D19D07C04BD095837BFD2DB2EAB90C97582CD33391B37D7CB41446B086E521D`.

Packaged assets contain profiles, the public-suffix database, the wordmark license and legal HTML;
the temporary test playlist and local device diagnostic archives are absent. Version numbers
were not incremented and the APK was not published to GitHub in this pass. Physical tests above
used the separate debug package, not this minified release APK.

## Limits

The new regression injects a specific engine error; it does not reproduce a particular IPTV
provider's moving live window or certify real Chromecast/Hisense interoperability. Native
player lifecycle fixtures use synthetic HLS and check engine state, not decoded moving frames.
Long-duration playback, OEM TV firmware and network topology remain separate acceptance work.
