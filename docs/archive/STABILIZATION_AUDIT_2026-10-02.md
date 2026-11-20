# Targeted stabilization audit — 2026-10-02

## Scope and method

The requested order is analysis, reproduction, minimal fixes, then regression checks.
This pass traces phone/TV remote ownership, playlist/backup persistence, icon resolution
and rendering, and player lifecycle/timer ownership. It is not a certification of every
line, device firmware, network condition or the entire release.

Existing work in the dirty checkout is preserved. No dependency/version update, publication,
data clearing, network reconfiguration or computer shutdown is requested by this pass.

## Findings and execution paths

### P2 — Artwork state outlives the channel it belongs to

- Files: `ui/components/ChannelIcon.kt`, `ui/components/ArtworkTone.kt`.
- Path: player/mini-player changes `channel` → `produceState` restarts its producer, but
  retains the previous remembered value → the new resolver suspends on cache/network work.
- Risk: the new channel is labelled with the previous channel's logo and artwork tint
  until resolution finishes. If it stalls, this misleading combination persists.
- Fix strategy: key the remembered render/producer state by channel and explicit artwork
  refresh generation. Do not key by a frequently recreated callback or wall-clock tick.
- Regression: establish a decoded logo/non-null tint, replace the channel with a deliberately
  suspended resolver, and assert immediate new-channel initials/no old tint.

### P2 — A decoded-image error cannot recover at the same cache path

- File: `ui/components/ChannelIcon.kt`.
- Path: Coil error sets `isDecodeError = true` → the image leaves composition → cache repair/
  refill and an explicit `refreshKey` change resolve the same `File` → `remember(iconFile)`
  keeps the error flag true → no new image request is made.
- Consequence: valid repaired artwork still displays initials until this component is recreated.
- Fix strategy: retire the error and painter together with the resolution generation. Keep
  the fallback after a real error; do not automatically retry on every recomposition.
- Regression: fail decoding an empty temporary file, repair it in place, refresh, and require
  a successful new decode with the initials removed.

### P2 — Visible Favorites do not refresh when artwork becomes available

- Files: `ui/nav/RootScaffold.kt`, `ui/favorites/FavoritesScreen.kt`.
- Path: EPG index or `IconPrefetchUiState.completedRuns` changes → other screens pass a new
  icon refresh key → Favorites passes only the unchanged channel and resolver to `ChannelIcon`.
- Consequence: favorites that initially resolved to no icon stay as initials while the same
  channels on Home/Channels can already show their logos.
- Fix strategy: pass the existing EPG/prefetch refresh signal through Favorites to its rows;
  no new collector, worker pool, polling or network policy.

### P2 — Sleep timer excludes device deep sleep

- Files: `player/PlayerSleepTimer.kt`, callers in `player/PlayerViewModel.kt` and
  `ui/player/PlayerHost.kt`.
- Path: user starts timer → phone goes to sleep while remote playback continues → deadline
  uses `System.nanoTime()` (CPU monotonic uptime, excluding suspend) → on wake the timer
  still has time left even though its intended elapsed duration has passed.
- Fix strategy: use Android's monotonic `SystemClock.elapsedRealtime`, which includes suspend,
  while preserving cancellation, the existing coroutine owner and injected test clocks.
- Before-fix evidence: the default-clock regression simulates four seconds of deep sleep
  during a three-second timer. Uptime stays constant and elapsed realtime increases; the first
  coroutine tick after wake reports **0 expirations instead of 1**.
- Limit: this does not give a coroutine permission to wake a suspended/killed process, and
  does not promise exact remote shutdown during Doze. No alarm permission or wake lock is added.

## Rejected/limited claims

- The source-list writer serializes durable writes; skipped obsolete generations alone are
  not evidence of lost data. Existing startup and backup generation guards were traced.
- The remote socket reader/monitor already detects idle EOF; this is not a newly found bug.
- Initially ADB listed no devices, and the previously authorized Xiaomi TV endpoint did not
  answer the direct connection attempt. Mi A2 appeared at the final availability check;
  its subsequent native results are recorded below. The TV itself remains unverified this pass.
- Initial artwork fixtures needed correction: a full-bright red intentionally has no tone
  under `ArtworkTonePolicy`, and the host cannot use the device window-capture path. These
  fixture failures are not counted as application bugs.
- The host artwork regressions use Coil's real software `BitmapFactoryDecoder`, with hardware
  bitmaps disabled only in their isolated test loader. Production decoder configuration is
  unchanged. The original singleton loader is restored after each test.

## Changes and focused evidence

- `ChannelIcon`: a keyed composition owns the producer, returned file, error flag and image
  painter together. Old work is cancelled, and late results belong only to the retired state.
- `rememberArtworkTone`: the same channel/refresh key also resets the remembered tint.
- `FavoritesScreen`/`RootScaffold`: the existing EPG-index/prefetch-completion signal reaches
  each visible favorite logo, without adding another data collector.
- `PlayerSleepTimer`: only the default clock changes to `elapsedRealtime`. Existing owner,
  tick interval, replacement/cancellation and playback-stop callbacks remain intact.

Four corrected before-fix tests failed at the intended assertions: old logo, old tint,
repaired-file decode never restarting, and elapsed sleep deadline not expiring. Their XML
evidence is retained locally under the ignored `app/build/device-audit/stabilization-20261002/`.
The Favorites omission is evidenced by the original call chain; its new behavior test checks
both prefetch completion and EPG replacement.

Seven new tests were added (six UI/Coil state tests and one Android-clock test). They also check
that ordinary recomposition does not retry a broken image indefinitely, and rapid channel changes
cancel old resolvers without allowing late results to overwrite the newest channel.

Focused verification passed **25 tests**, zero failures/errors, plus app detekt. The architecture,
empty-detekt-baseline, documentation-reference, Play-distribution and sensitive-log-variable
scripts all passed without weakening rules, changing baselines or recording new screenshot goldens.

## Full regression

Successful command (offline, one Gradle worker, JDK 21):

```text
:core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:detekt
:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
-Puacast.requireSigning=true --offline --max-workers=1 --no-daemon
```

`BUILD SUCCESSFUL in 10m 11s`; 203 tasks, 45 executed and 158 up-to-date.

| Check | Result |
| --- | --- |
| Debug unit/UI + screenshot verification | 2,383 tests, 0 failures/errors/skips |
| Release unit | 2,116 tests, 0 failures/errors/skips |
| Play unit | 2,116 tests, 0 failures/errors/skips |
| Core | unchanged; Gradle reused the successful 126-test result |
| Lint debug/release/Play | passed; each report has the existing `OldTargetApi` hint only |
| App/core detekt | passed; no baseline suppression added |
| Debug / Android test / signed release APK builds | passed |

Counts across variants are repeated executions of substantially overlapping suites, not a count
of distinct test cases. Compose-manifest tests intentionally run in debug, not release/Play.
R8 still emits two build-tool `ClassFileResourceProvider` asynchronous-parsing warnings. They
were not suppressed; neither the SDK nor AGP was upgraded incidentally during this stabilization.

### Physical Mi A2 — Android 11 / API 30

A separate `com.uacastplayer.debug` package and its test APK were installed. The existing
`com.uacastplayer` package was not replaced, uninstalled or cleared. Tests use isolated Activity
content, owned temporary image files and mock consent/PIN actions; no real purchases or exports.

After the phone became available, four native artwork tests were added using the production
decoder configuration (no forced legacy decoder). `assembleDebugAndroidTest`, app detekt and
debug lint passed again in 1m 1s. No production source changed after the full gate above.

- Artwork + TV-style dialog tests: **OK (15 tests), 19.789s**. This includes all four new
  artwork regressions plus all eight confirmation/PIN-input and three empty-modal tests.
- Remote lifecycle/crypto loopback: **OK (5 tests), 34.290s**. Background/recreation,
  replaced connection ownership, cancelled handshake, receiver EOF without a button press,
  ViewModel/socket cleanup and real platform key derivation passed. Total native: **20 tests**.
- Raw local output: `app/build/device-audit/stabilization-20261002/mi-a2-artwork-and-dialogs.txt`
  and `mi-a2-remote-lifecycle.txt` in the same ignored directory.

The TV-style dialog tests on this phone exercise real Android windows and application D-pad
routing. They do not certify Xiaomi TV firmware, its OEM remote/IME, or actual phone-to-TV LAN
delivery. No result is claimed for the interrupted September 30 final native TV suite, prolonged
playback, provider streams, Hisense VIDAA/DLNA, process-kill recovery or exact Doze wake deadlines.

### Additional Mi A2 verification after device clarification

The user clarified that the connected Xiaomi is the **Mi A2 phone**, not the Xiaomi TV.
The same debug/test APK hashes listed below were used; no production source or version changed.

- `MediaSessionRuntimeInstrumentedTest` (3), `ProxyServerInstrumentedTest` (9), and
  `DlnaClientInstrumentedTest` (6): **OK (18 tests), 6.756s**.
- `ProxyFlattenInstrumentedTest` (4) and `ProxyRemuxInstrumentedTest` (5):
  **OK (9 tests), 2.966s**.
- After the user unlocked the phone, `PlayerLifecycleInstrumentedTest` (14),
  `PlayerVideoFitInstrumentedTest` (4), and `PlayerRestorationRegressionInstrumentedTest` (2):
  **OK (20 tests), 94.306s**. This covers repeated open/close and mini/fullscreen cycles,
  channel changes, rapid play/pause, background/foreground, sleep expiry, retained player
  identity/current channel, video-fit controls and parental filtering after Activity recreation.
- These 27 additional cases cover command ownership, stale proxy session URLs, Range/CORS,
  parallel requests, DLNA refusals/retries/unreachable endpoints, redirected HLS segment bases,
  HEAD responses, manifest fallback and reuse of the upstream remux connection.
- Raw logs: `app/build/device-audit/stabilization-20261002/mi-a2-media-network.txt` and
  `mi-a2-proxy-media-routes.txt`; the completed UI run is
  `app/build/device-audit/instrumented-783ff29fb6e84f8f8ad3d2f2be44424c.txt`.
  Together with the earlier 20 cases, **67 native cases passed** on this phone in this
  stabilization pass (47 additional). No newly confirmed production defect was found in them.

The initial expanded 38-case player/network run did **not** pass: two UI setup attempts timed
out waiting for a Compose hierarchy. Device diagnostics showed `isKeyguardShowing=true`,
`mCurrentFocus=NotificationShade` and `mWakefulness=Dozing`. The run was deliberately stopped;
its subsequent `Process crashed` runner message is from that force-stop, not evidence of an
independent application crash. After explicit user confirmation that the phone was unlocked,
all 20 selected UI cases passed in a fresh run. Do not count the initial abort as a passing
suite or change production code to hide this environmental prerequisite.

The preserved-device runner restored the original debug `files` and `shared_prefs` in its
`finally` block. A private recovery archive and test fixture state were retained locally;
neither should be published. The primary `com.uacastplayer` package and its data were not touched.
The 27 non-UI cases use isolated loopback/MediaSession fixtures. The 20 player UI cases use a
synthetic empty HLS stream and assert engine intent/lifecycle, not decoded moving frames.
These results do not prove real TV interoperability, Wi-Fi delivery, video decoding or
sustained playback. No primary app data, lock-screen configuration or screen-timeout setting
was changed, and no app code was modified merely to make this device run pass.

## Artifacts

The metadata in this section records the build **before** the subsequent live-window fix.
See [live-window stabilization](PLAYER_LIVE_WINDOW_STABILIZATION_2026-10-02.md) for the later
production change, before/after regressions and updated build/device verification. The local
APK output paths are reused by builds; use each report's hash to distinguish its artifact.

Version unchanged: **0.9.7**, universal versionCode **164**. These are local verification builds,
not a new GitHub release or an updater-detectable version increment.

- `app/build/outputs/apk/release/app-universal-release.apk`: 24,154,856 bytes;
  SHA-256 `BB2C8D90F7AA9EBD4F1C0FEC58AAD8563E29AA5FC2A219A40C4957DAF12746B0`.
  `apksigner verify --verbose`: verified, v2/v3, one signer. Not physically executed on the phone.
- Installed debug APK SHA-256:
  `B87B0F4DDAF6A7DC6C5CC0A8D51AD1DFA693DC8099FCCF155384795ACA1DF14B`.
- Native-test APK SHA-256:
  `CB0777975AFC12619DFFEF6EA877F21A5023325C50DC861CF34869EF51544F4E`.
