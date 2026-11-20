# Cast in-band diagnostic stabilization — 2026-09-29

The later `CAST_PROXY_DIRECT_HANDOFF_VERIFICATION_2026-09-29.md` records two additional fixes,
their regression gate, and the newer local APK hash. This report's test counts and artifact hash
remain historical evidence for the in-band diagnostic pass, not the current final local file.

## Scope

This follow-up closes the direct receiver/diagnostic overlap left open in
`CAST_DIAGNOSTIC_FIX_VERIFICATION_2026-09-29.md`. It is not a claim that every application line,
physical receiver, firmware version, or provider has been validated. Unrelated worktree changes
were preserved. No publication, version bump, device installation, or shutdown was performed.

## Confirmed production defect

**P1: a sender diagnostic could displace the Cast receiver's stream.**

Execution before the fix:

1. `setActiveChannel()` → `startPlayback()` → `loadDirectWithWatchdog()`.
2. `loadOnReceiver()` calls the real Cast SDK `load(originUrl)`.
3. An uncached channel starts `CastDiagnosticCoordinator.probe()` in a watchdog child coroutine.
4. `TsFirstSegmentDiagnostic` opens another origin GET while the receiver can already be streaming.
5. A provider permitting only one connection can terminate the receiver stream or reject a request.

`CastDirectNetworkOwnershipTest` exercises the actual repository with a shadowed SDK and real
HTTP sockets. Its modeled receiver holds the first connection to a one-slot origin. A second
connection explicitly closes that first connection. Before the production change the test failed:
`The diagnostic GET displaced the receiver's one allowed origin connection`.
Initial SDK-fixture constructor errors were repaired; those setup failures were not bug evidence.

Moving the diagnostic before `load()` was rejected: an earlier channel may still be playing on
the receiver during a switch. The new Cast path performs no standalone diagnostic GET at all.

## Implementation and stability boundaries

- `ProxyRouteSelector` returns the source/verdict it already computed for routing. Raw-TS
  metadata uses the existing 128-KiB remux probe, without another request, peek, or codec parse.
- `ProxyServer` publishes only successful current root observations. Nested HLS resources cannot
  establish the root's codec verdict. Wrapped raw streams update their root using the existing
  inner response. Disabled remux still disables raw probing; HTTP error bodies yield no metadata.
- Observations carry the exact producer lease. `CastProxySession` adds a Cast attempt identity.
  The repository dispatches to its Main scope and rechecks attempt, active resource, and lease
  before changing the cache or state. A → B → A and same-token stop/start cannot revive old data.
- Observer exceptions cannot interrupt HTTP response serving. Observations retain no upstream
  response, socket, Activity, or UI object.
- Cache keys now match proxy request identity: URL plus sanitized User-Agent/Referer. Different
  access headers no longer reuse another request's verdict. Existing LRU/TTL/merge policies remain.
- Cached compatible raw TS can select proxy before an unnecessary direct load. Cached codec
  errors preserve receiver media replacement before being shown, as in the original handoff.
- The direct watchdog retains its four-second timeout and adds an explicit load-generation check.

## Regression caught during implementation

A first implementation returned before receiver `load()` for a cached incompatible codec.
Tracing channel handoff showed that this could leave the old receiver media playing while the UI
already referred to the new channel. A regression requiring media replacement failed with:
`A skipped load could leave the previous receiver media playing expected:<1> but was:<0>`.

The early return was removed before delivery. The codec flag is applied after the existing
receiver replacement path; no new stop/session-ending protocol was introduced.

## Tests

- `CastDirectNetworkOwnershipTest`: held receiver stream; 30 rapid switches; late SDK results;
  cached-incompatible channel still replaces receiver media.
- `CastProxyDiagnosticOwnershipTest`: current attempt; same-root new attempt; 30 switches;
  queued observation after stop/restart; changed access headers; no speculative origin fetches.
- `ProxySourceObservationTest`: shared raw response; same-root validity; A → B → A rejection;
  stop/start rejection; changed headers; 403 exclusion; disabled raw probe; wrapper metadata;
  serving continues after an observer failure.
- `CastDiagnosticCoordinatorTest`: explicit observations, stable cache reads, URL/header isolation,
  header sanitization/display-field independence, strongest-verdict merge.
- Existing local-network ownership, proxy routing/admission/cancellation/remux/large-response,
  standalone diagnostic HTTP, and Cast generation/reducer/watchdog tests remain enabled.

## Verification

Final repeat completed successfully in **5m 1s** after correcting the cached-codec handoff
regression. The final sources and all three app variants were checked; existing core results
were reusable because core code was unchanged in this follow-up.

```powershell
.\gradlew :core:test :core:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:testPlayUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleRelease '-Puacast.requireSigning=true' --max-workers=2 --offline --no-daemon --console=plain
```

Counts were read from the final JUnit XML reports, not inferred from Gradle task totals:

| Variant | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| core | 120 | 0 | 0 | 0 |
| Debug | 2,280 | 0 | 0 | 0 |
| Release | 2,044 | 0 | 0 | 0 |
| Play | 2,044 | 0 | 0 | 0 |

The app counts include repeated coverage across build variants, not 6,368 different test cases.
Relative to the previous diagnostic pass, each app variant has 18 additional cases net.
Both lint tasks, both detekt tasks, `scripts/check-architecture-boundaries.sh`, and
`git diff --check` passed. No new Kotlin compiler warning remains. Two existing R8 warnings about
the class-file resource provider not supporting async parsing remain; they are toolchain warnings,
not test or signing failures. No baseline or lint threshold was relaxed.

### Final local artifact

- File: `app/build/outputs/apk/release/app-universal-release.apk`.
- Version: `0.9.7`, universal versionCode `164`, application ID `com.uacastplayer`.
- Size: **24,068,900 bytes**; final file modification time: 2026-09-29 17:44:34 local.
- SHA-256: `F2AFE86E7966E66525C35870704495F6F8FCEE1EA21D4B4411F168E7E16199D2`.
- Android build-tools 36.1.0 `apksigner verify --verbose`: **Verifies**, v2/v3 true, one signer.
- This is a local rebuild, **not** a newly published 0.9.7 release. Do not replace assets under
  the existing tag or expect an installed 0.9.7/code164 build to detect it as a version upgrade.
  New distribution needs a version decision and the remaining hardware/CI checks.

## Remaining limits

- For an uncached raw-TS source the first direct attempt may wait four seconds before proxy fallback.
  This is deliberate: early source detection must not introduce another consumer of the stream.
- HLS codec metadata remains Unknown unless already established for that request; Cast playback
  no longer fetches a separate HLS segment or arbitrary variant for a verdict. Actual receiver
  failures continue through the existing error/recovery path.
- Eliminating the sender diagnostic does not prove that every receiver cancels its old origin
  promptly during SDK media replacement. Device-specific handoff still needs a physical test.
- No ADB device was connected. Real Chromecast acceptance and Hisense VIDAA DLNA “Error de red”
  remain unverified by these host-side tests. This is a Chromecast/proxy fix, not proof of a DLNA fix.
