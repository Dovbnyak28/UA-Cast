# Cast diagnostic stability pass — 2026-09-29

Subsequent follow-up: `CAST_INBAND_DIAGNOSTICS_VERIFICATION_2026-09-29.md` closes the direct
receiver/probe overlap identified as open in this report. Standalone probing is no longer wired
to Cast playback; the tests and APK hash below document this earlier pass, not the final artifact.

Scope: the channel → Cast repository → codec/source probe → route decision path. Existing unrelated
worktree changes were preserved. This is not a claim that every production line has been reviewed.

## Confirmed defects addressed

1. **P1 — local playback displaced without casting.** `setActiveChannel()` scheduled an unsolicited
   GET after 1.5 seconds, while Media3 was already using the origin. The real-repository regression
   `CastLocalNetworkOwnershipTest` held a first connection to a single-slot origin and proved the
   old warm-up opened a second GET and displaced it. The no-session path now only remembers the
   channel. Removed its unused debounce/job lifecycle; explicit Cast probing and cached results
   still work. Trade-off: the first Cast attempt no longer receives a pre-warmed local verdict.
2. **P2 — provider errors accepted as codec evidence.** Neither the initial request nor the segment
   request checked HTTP success. A 404 TS body became `RawTs`; a 403 playlist caused another fetch;
   a 403 segment declaring MPEG-2 became an incompatibility verdict. Both response boundaries now
   reject non-successful status codes before reading/classifying the body. A successful 206 still
   yields the expected codec.
3. **P2 — access headers did not match playback.** The diagnostic omitted the channel's user agent
   and referrer, unlike Media3 and the proxy. Both initial and segment probes now carry the same
   sanitized values/defaults. A provider fixture that rejects the wrong headers verifies both
   requests; CR/LF header injection inputs are discarded rather than sent.
4. **P2 — proven HLS source kind was lost on segment I/O failure.** A connection reset inside the
   segment fetch escaped to the outer unknown-source fallback. It now invalidates only codec
   metadata. The source remains HLS, so extensionless URLs retain the detected content type.
   Cancellation is not swallowed; the existing real-socket cancellation tests still pass.
5. **P2 — master variant treated as media bytes.** `firstMediaSegmentLine()` returned a master
   variant URL despite its documented media-only contract. The diagnostic fetched playlist text
   as TS. Master tags now prevent that extra fetch, preserving HLS with unknown codecs. This is
   deliberately not recursive variant selection: an arbitrary variant's codec must not hard-block
   a different variant the receiver can play.

## Verification

- Before production fixes: 6 focused tests, **5 failed** on the old code and the successful-206
  control passed. Failures reproduced local connection displacement, erroneous 404/403 handling,
  and a master variant's extra fetch. Header and segment-I/O regressions were then added.
- After fixes: **32 targeted tests passed**, including real-socket initial/segment cancellation,
  cache/stale-result behavior, classify → codec → Cast routing, and local-player handback.
- Full gate passed in **4m 35s**: core **120**, debug **2,262**, release **2,026**, Play **2,026**
  tests, with zero failures, errors or skipped cases in each executed suite. Compose-manifest tests
  run in debug only under the project's existing variant configuration.
- `:core:detekt`, `:app:detekt`, `:app:lintDebug`, `:app:lintRelease`, the architecture-boundary
  script, `git diff --check`, and signed `:app:assembleRelease` all passed. The first full gate
  stopped on a long HTTP-header line in the new test fixture; it was wrapped and the complete
  gate rerun without weakening rules or exclusions.
- The same two existing R8 toolchain warnings about class-file-provider async parsing remain;
  they did not fail compilation or signing. No new compiler warning was suppressed.
- ADB returned an empty device list. No phone/TV test is implied by these host-side results.

Exact full-gate command (PowerShell):

```powershell
.\gradlew :core:test :core:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:testPlayUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleRelease '-Puacast.requireSigning=true' --max-workers=2 --offline --no-daemon --console=plain
```

## Local APK, not a published release

- `app/build/outputs/apk/release/app-universal-release.apk`
- Version **0.9.7**, universal versionCode **164**; **24,068,900 bytes**.
- Built **2026-09-29 17:02:27** in the host's local clock.
- `apksigner verify --verbose`: valid v2/v3 signatures, one signer.
- SHA-256: `0A717B722076EB3784D191894E01DCD5CCF83E02A788A538687D1561A9C1FB43`.

This later local build supersedes the local APK hash recorded by the earlier APK/DLNA pass today.
Do not replace assets under the already published 0.9.7 tag. A new public distribution still
requires a version decision, release/hardware verification, and publication as a new release.

## Still open — do not claim resolved

- `loadDirectWithWatchdog()` can still probe while the Cast receiver fetches the origin. The local
  warm-up fix does not serialize those two consumers. A dedicated receiver/probe ordering test and
  a bounded startup strategy are required before changing this flow.
- Real Hisense VIDAA “Error de red”, firmware-specific DLNA behavior, and real Cast receiver
  acceptance were not tested by these host-side regressions.
- No GitHub publication, version bump, device installation, or shutdown is part of this pass.
