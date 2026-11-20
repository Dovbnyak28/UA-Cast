# Cast proxy/direct handoff stabilization — 2026-09-29

## Scope

Follow-up to `CAST_INBAND_DIAGNOSTICS_VERIFICATION_2026-09-29.md`. This batch examines the
actual repository handoff, proxy producer lifetime, receiver status ownership, and shared service
cleanup. It does not claim complete source-line coverage or physical receiver acceptance.
Unrelated worktree changes were preserved. No publication, version bump, or device installation
was performed.

## Confirmed bugs

### P1 — old proxy producer survives a switch to direct playback

Execution before the fix:

1. A proxy/remux channel has an active `RawTsRemuxSession` and an open origin response.
2. `setActiveChannel()` calls `startPlayback()` for another, direct-first channel.
3. `CastProxySession.beginPlaybackAttempt()` advances the attempt and clears `activeResourceId`.
   This invalidates observations but does not stop the producer or server.
4. `loadDirectWithWatchdog()` loads the new origin URL on the receiver without retiring the proxy.
5. The old remux reader continues independently of receiver requests. A single-slot provider
   can reject the new receiver stream; the old proxy URL and foreground protection also remain.
6. A successful SDK load calls `confirmActiveSession()`, which only removes a *draining* remux
   session. It does not remove the still-active old producer on this direct path.

`CastProxyDirectHandoffTest` uses real proxy sockets and the actual remux reader. Its origin is
an OkHttp-intercepted ownership model: a direct request returns 403 while the old response is open,
and 200 after it is closed. This is not a physical receiver or a real remote provider.

Before the fix, the intended assertions failed:

- `Old proxy reader occupied the provider's sole connection expected:<200> but was:<403>`.
- `Old URL returned response bytes after retirement expected:<-1> but was:<72>`.

Fix: `loadDirectWithWatchdog()` stops the Chromecast proxy before calling SDK `load()`. This
cancels owned requests/producers, closes local sockets, clears resources, and requests owner-scoped
service cleanup. It does not end the Cast SDK session. A subsequent proxy fallback can restart
the server with the same token; it is not required to reuse the old listening port.

Proxy → proxy is deliberately unchanged: when host/token remain the same it preserves the port,
uses `registerRoot()` to stop the old reader, and retains the existing completed-segment grace path.
Changing the host legitimately restarts the server; an initial test incorrectly expected the same
port across a host change and was corrected. That fixture error was not a production defect.

### P1 — previous PLAYING state disables watchdogs for replacement media

Execution before the fix:

1. Channel A is `PLAYING` in `CastPlaybackState`.
2. Channel B replaces it through `loadOnReceiver()`. Only `loadPhase` changes to `LOADING`;
   `receiverStatus` retains A's `PLAYING`.
3. `CastStatusContentPolicy` correctly rejects an A status callback with the old content ID.
   Rejection alone does not remove the old status already stored in state.
4. B can fail to start without providing a matching status. SDK load acknowledgement is not
   evidence that B reached playback; its success reducer only changes `loadPhase`.
5. The direct watchdog sees the stored `PLAYING` and skips fallback. The proxy load-stall watchdog
   can similarly settle using that stale state. UI and recovery can therefore claim progress that
   has not happened for the replacement.

Two regressions failed before the state fix:

- `Old PLAYING is not evidence that the replacement is playing expected:<IDLE> but was:<PLAYING>`.
- `A stalled replacement must reach proxy fallback expected:<2> but was:<1>`.

Fix: every `loadOnReceiver()` initializes pending media with `LOADING`, neutral `IDLE`, and
`IdleReason.NONE`. It does not feed a synthetic terminal event through the receiver reducer or
disconnect the session. Only an accepted matching receiver status can establish `PLAYING`/`PAUSED`
for the new load. Existing content/session and load-generation guards remain enabled.

## Regression coverage

The new suite contains 11 cases:

1. Old origin response closes before the new direct SDK load can fetch.
2. Retired proxy URL refuses/resets/returns EOF, not response bytes or a read timeout.
3. Negative control: an unretired producer really causes HTTP 403 in the one-slot model.
4. Direct watchdog restarts the proxy for the new channel after retiring the old server.
5. Proxy → proxy preserves its port with unchanged host/token and retires the old producer.
6. Direct handoff removes Chromecast foreground protection and releases its wake lock.
7. Another active DLNA owner retains the shared foreground service and wake lock.
8. New media does not inherit previous-channel `PLAYING`.
9. An old-content `PLAYING` callback cannot silence the direct replacement watchdog.
10. Matching replacement `PLAYING` settles Direct without an unwanted proxy fallback.
11. Old `PLAYING` cannot settle a silent new proxy load; it enters recovery instead.

The repository and proxy are actual production objects. Cast SDK loading is shadowed; MediaStatus
JSON is parsed by the SDK and dispatched through the repository's real ownership checks. The Wi-Fi
address and service lifecycle are supplied by Robolectric. Existing rapid-switch, late-load-result,
proxy observation/lease, remux/flattened/upstream ownership, route, reducer, and watchdog tests
remain part of verification. No detekt baseline or lint threshold is relaxed.

## Verification

The focused repeat passed 48 Cast/proxy ownership cases, including all 11 new handoff cases,
with zero failures/errors/skips; `:app:detekt` passed in that repeat. The initial failures quoted
above are from the intended assertions, not fixture compilation/setup failures.

The full gate passed in **4m 48s**:

```powershell
.\gradlew :core:test :core:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:testPlayUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleRelease '-Puacast.requireSigning=true' --max-workers=2 --offline --no-daemon --console=plain
```

Counts from final JUnit XML reports:

| Variant | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| core | 120 | 0 | 0 | 0 |
| Debug | 2,291 | 0 | 0 | 0 |
| Release | 2,055 | 0 | 0 | 0 |
| Play | 2,055 | 0 | 0 | 0 |

All three app test variants reran against this source. Core results were reused because its
source was unchanged. Each app variant adds 11 cases over the in-band diagnostics pass; repeated
cases across build variants are not different bugs or different unique test designs.
Both lint and detekt gates, `scripts/check-architecture-boundaries.sh`, and `git diff --check`
passed. No new Kotlin warning remains. Two existing R8 toolchain warnings about the class-file
resource provider not supporting async parsing remain; they are not test or signing failures.

The additional `:app:verifyRoborazziDebug` golden-image verification passed in **1m 53s**. It reran
the whole Debug suite with golden verification enabled; the final Debug XML again contains 2,291
tests and zero failures/errors/skips. `git diff --name-only -- '*.png'` is empty: golden images were
not re-recorded or relaxed. This repeat is not counted as another set of unique test cases.

### Local artifact

- File: `app/build/outputs/apk/release/app-universal-release.apk`.
- Version: `0.9.7`, universal versionCode `164`, application ID `com.uacastplayer`.
- Size: **24,068,900 bytes**; modification time: 2026-09-29 18:15:39 local.
- SHA-256: `AAECE8D29F2CAD8A52BA4636BE516548208978880E1CE411A3CCF88ACA14DD05`.
- Android build-tools 36.1.0 `apksigner verify --verbose`: **Verifies**, v2/v3 true, one signer.
- This hash supersedes the in-band report's earlier local artifact, not a published release asset.
  No GitHub release or assets were changed.

## Remaining limits

- These tests do not prove when a physical provider notices TCP closure, or how promptly a
  Chromecast receiver cancels its own superseded origin stream during SDK media replacement.
- Same-content-ID status updates from different SDK loads cannot always be distinguished by the
  content-ID guard alone; this batch does not introduce a custom receiver protocol.
- An uncached raw-TS direct attempt can still wait four seconds before fallback. HLS codecs still
  do not cause an independent sender diagnostic GET.
- ADB reported no connected devices. Physical Chromecast and Hisense VIDAA DLNA “Error de red”
  acceptance remains unverified. This is not an on-device/R8-runtime or real Play Billing test.
- A local rebuild of 0.9.7/code164 is not a newer installable version for an existing 0.9.7/code164
  user. Do not replace published assets under that tag; new distribution needs a version decision
  and hardware/CI checks.
