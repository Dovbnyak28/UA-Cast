# Code audit ledger — 2026-09-24

Baseline: `d6728a6632d8bde9bd035a561118a621059321d7` (`codex/release-0.9.6`).
Production scope: 482 Kotlin/Java files, approximately 45,158 lines in `app/src/main` and
`core/src/main`. This ledger distinguishes a traced execution path from a whole-repository claim:
the work below does **not** mean every production line has been manually reviewed.

## Confirmed paths and fixes in this pass

1. **Cast/DLNA shared foreground service:** `CastProxyService.stop()` called Android's global
   `stopService()` when `runningInstance` was still null. A pending start for the other target could
   therefore lose foreground protection. Stale commands already carry per-target generations;
   stopping now advances the generation and lets a pending obsolete start reject itself without
   cancelling the other target. Covered by `CastProxyOwnershipPolicyTest`.
2. **DLNA SOAP control:** default OkHttp redirect handling could turn a renderer's redirected SOAP
   POST into a GET and treat the eventual 200 as a successful Play/SetURI/volume/status response.
   The AVTransport, RenderingControl, and transport-health clients now refuse redirects. Local
   socket regressions in `DlnaSoapRedirectTest` prove that no second request is sent.
3. **Player UI state during remote playback:** `applyChannelSwitch()` stopped Media3 for Cast/DLNA
   yet wrote `isBuffering=true` afterwards, leaving an indefinite local loading indicator. It now
   records whether local preparation actually occurred. Both remote paths are covered by
   `PlayerCastHandbackTest`.
4. **SSDP discovery deadline:** `InetAddress.getAllByName()` ran in the bounded UDP receive loop.
   Hostname resolution now happens in parallel description-fetch workers; numeric IPv4 location
   addresses are rejected immediately when they do not match the packet sender. Covered by
   `SsdpResponseParserTest`.
5. **System activity launch denial:** the update-permission screen, SAF document picker, battery
   optimization screen and summary-only diagnostics email handled an absent activity but not a
   present activity that throws `SecurityException` (OEM export restriction or device policy).
   This propagated from user tap handlers on Main. Regression tests reproduced each exception
   before the fix and now require a safe, retryable/fallback outcome.

## Verification

- Latest full gate: `:core:test`: 120 tests; `:app:testReleaseUnitTest`: 2,012 tests;
  `:app:testPlayUnitTest`: 2,012 tests; `:app:testDebugUnitTest`: 2,247 tests,
  all with zero failures and errors. The four system-launch regressions failed before their fixes
  and passed afterwards.
- `:app:verifyRoborazziDebug`, `:app:lintRelease`, `:core:detekt`, `:app:detekt`,
  `:app:assembleRelease -Puacast.requireSigning=true`, and
  `scripts/check-architecture-boundaries.sh` all passed. The latest full Gradle gate finished in
  4m 21s with `--max-workers=2`.
- The local universal `0.9.7` release APK (versionCode 164, 24,068,248 bytes) passed
  `apksigner verify` with v2 and v3 signatures. SHA-256:
  `8D888AF4A5BC4F1F9484D807D9302AE37A99D82826B8D4D0A5F3736B0B6834F7`.
- One run under unrestricted Gradle parallelism had a timeout in the existing
  `ProxyLargeResponseTest` (262,082 of 262,144 body bytes observed). The isolated test and the
  complete gate with `--max-workers=2` passed. The test's 5-second client read timeout was shorter
  than the proxy's intentional 15-second per-write timeout. The test now allows 20 seconds for
  delivery and still verifies every byte, EOF, and socket cleanup. This is a test-harness fix,
  **not** proof of an application-server defect; a repeat full gate remains required.
- No ADB device was connected for a hardware/Hisense validation in this pass.

## Diagnostic ownership follow-up — 2026-09-29

- The local-playback part was reproduced by `CastLocalNetworkOwnershipTest`: the real repository,
  without a Cast session, opened a second GET that displaced an already-held connection on a
  single-slot test origin. The speculative warm-up has now been removed. Explicit Cast diagnostics
  and cached verdicts remain; the first Cast attempt no longer benefits from a local pre-warm.
- The **direct receiver/probe overlap remains open**: `loadDirectWithWatchdog()` still starts a
  diagnostic while the receiver can fetch the same origin. That must not be marked fixed by the
  local-playback regression. Receiver/probe ordering and its startup/fallback trade-off require
  a separate integration test.
- This follow-up also rejects non-successful diagnostic HTTP responses, carries sanitized channel
  headers, keeps HLS source kind after a segment failure, and stops fetching master variant URLs
  as TS segments. See `CAST_DIAGNOSTIC_FIX_VERIFICATION_2026-09-29.md` for test evidence.

## Subsequent Cast ownership closure — 2026-09-29

The direct receiver/probe overlap listed above was reproduced by
`CastDirectNetworkOwnershipTest` with the actual repository, a shadowed Cast SDK, and real HTTP
sockets. The previous sender diagnostic displaced the modeled receiver's held stream.
Cast playback now obtains diagnostics from already-fetched proxy responses instead of opening
another GET. Cast attempt plus exact producer-lease checks reject queued stale observations;
cache identity now includes sanitized access headers.

A proposed cached-codec early return was also rejected by a regression before delivery: media
replacement must still occur or the previous receiver channel can remain active. See
`CAST_INBAND_DIAGNOSTICS_VERIFICATION_2026-09-29.md` for the final repeat gate, artifact identity,
startup/HLS trade-offs, and remaining physical-device limits. The earlier open-item statement is
historical, not the current status of this sender diagnostic conflict.

## Proxy/direct handoff and receiver-state closure — 2026-09-29

Two further P1 defects were reproduced through the actual Cast repository:

- Switching proxy → direct left the old remux producer/origin response alive. A one-slot origin
  ownership model returned 403 during the new SDK load, and the old proxy URL still served bytes.
  Direct loading now stops the Chromecast proxy before issuing the SDK load; later fallback can
  restart it. Proxy → proxy retains its unchanged-host/token port handoff.
- New receiver loads kept the previous media's `PLAYING` in state. Even correctly rejecting an
  old-content callback could leave that stored state settling both watchdogs. Loads now begin
  with neutral media status; an accepted current callback establishes actual playback.

`CastProxyDirectHandoffTest` adds 11 regressions and controls, including direct/proxy watchdogs,
origin retirement, a matching PLAYING control, real shared-service/wake-lock teardown, and an
unaffected DLNA owner. The full gate passed: core 120, Debug 2,291, Release 2,055, Play 2,055, with
zero failures/errors/skips; lint, detekt, architecture checks and APK v2/v3 signature verification
passed. See `CAST_PROXY_DIRECT_HANDOFF_VERIFICATION_2026-09-29.md` for proof, current local APK
identity, and the additional golden-image verification status. No device was connected; no
hardware acceptance, version bump, or publication is implied by these host-side results.

## Reviewed, without a new confirmed finding

- DLNA SSDP, description parsing, SOAP commands, connection serializer and teardown;
  Chromecast/DLNA service ownership and generation handling.
- Player ViewModel channel switch, remote handback, background/foreground and Media3 cleanup.
- Proxy accept/admission/stop and large-response test path.
- Playlist source add/switch/remove, parser and snapshot/persistence flow; backup import merge;
  update APK download/publish/install path; external activity launches and relevant exported
  components in the Android manifest.
- EPG download/parse/snapshot boundaries and the non-recursive `MiniJson` parser. A suspected
  nested-JSON stack overflow was rejected: this parser only accepts flat arrays of flat objects.
- Premium repository and Play Billing provider. `PremiumAvailability.STORE_IS_LIVE` remains false
  in this build; no real-store purchase or entitlement path has been exercised on Play.

## Next systematic batches

1. Proxy upstream/flattened HLS/remux response lifetime under concurrent stop, switch, and slow
   receiver; reproduce the large-response timeout before assigning it a severity.
2. Playlist parser, snapshot migration, and backup restore with corrupt/oversized inputs and
   cancellation at persistence boundaries.
3. EPG XML parsing/indexing/cache ownership and 128 MB-heap behavior.
4. Remaining Compose navigation/collection paths and billing/update install state transitions.

Keep pre-existing unrelated worktree changes out of any audit commit or release. Do not publish
the locally rebuilt 0.9.7 APK under the already published 0.9.7 tag; a new distribution requires
a version decision and another hardware/CI gate.

## Android TV phone-remote EOF follow-up — 2026-09-30

One further **P2 incorrect-state bug** was reproduced before its production fix: closing the
TV server's TCP connection without another button press left the phone ViewModel Connected.
The phone coroutine waited only for queued commands and the socket read lived only in send.
A single connection-owned reply reader and a structured ViewModel closure observer now retire
that state on EOF. Old ownership, ACK ordering, cancellation/close and bounded ACK waits are
pinned by nine new host-side regressions; the existing receiver authentication limits,
protocol and crypto have not changed. A silent network black hole is not the same as EOF
and is not claimed to be proactively detected while idle.

The targeted remote batch passed 32 tests. The subsequent complete application suites passed:
Debug 2363, Release 2113, Play 2113, all without failures/errors/skips; the unchanged core's
126-test result was up-to-date. See `REMOTE_EOF_STABILIZATION_2026-09-30.md` for source path,
pre-fix failure, new regressions, rejected suspicions and hardware limits. No device was
connected in that original build pass; no whole-repository/no-more-bugs claim or publication
is implied.

After the owner connected direct Wi-Fi, the current debug artifact was installed on Xiaomi
/ Android 9 without clearing data. Five native crypto/lifecycle tests passed (140.407 s),
including receiver EOF without a button press and successful retry pairing. Current native
moving-video/lifecycle/30-switch/Back cleanup acceptance passed separately (one test,
31.954 s). The redesigned banner was observed on the OEM launcher in normal and focused
states after refreshing its stale same-version-update cache without clearing storage.
Exact hashes, distinctions from older phone-to-TV LAN results and remaining acceptance:
`XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md`. No new production-code edit or release
publication was required in that device pass.
