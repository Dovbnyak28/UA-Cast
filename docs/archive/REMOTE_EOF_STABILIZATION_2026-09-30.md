# Phone remote EOF stabilization — 2026-09-30

## Confirmed bug: stale connected state after the TV closes its socket

Severity: **Medium / P2** (incorrect remote UI state, not an evidenced crash or playback loss).

Files/functions:

- `data/remote/PhoneRemoteClient.kt`: `connect()`, `send()`, reply/socket ownership.
- `remote/PhoneRemoteViewModel.kt`: `connect()` and its command-processing coroutine.
- `data/remote/TvRemoteServer.kt`: `close()` and active-client replacement/idle retirement
  provide the closing side; their protocol/limits were not modified.

Execution before the fix:

1. Pairing succeeded and the phone published `connected=true`.
2. Its coroutine suspended waiting for the next item in the bounded command channel.
3. TV stop/new-code/session retirement closed the socket, but the phone performed no read.
4. The phone kept showing Connected and remote controls until another button triggered
   `send()` and the first attempted ACK read failed.

`RemoteLifecycleTest.receiver closing an idle socket clears phone connected state without
a button press` was added **before production changes**. With the real TV server, phone
ViewModel and loopback TCP socket, it reached connected state, closed the server and then
failed with `TimeoutCancellationException` waiting for the phone to report failure. No button
was sent, so this could not pass via the old command-error path.

## Fix and unchanged contracts

One connection-owned daemon reader now owns post-pairing replies and observes idle EOF.
Only one pending command can own an ACK promise; sequence checking still occurs before a
command succeeds. Socket/framing/crypto failure closes the connection, completes the closure
signal and interrupts pending ACK waits. The ViewModel's structured child coroutine awaits
that signal; only the current client may publish failure. Replacement/disconnect cancels the
old owner and closes its socket, so old EOF cannot overwrite a new connection.

The idle reader blocks without polling, heartbeats or periodic work. The five-minute TV idle
expiry, eight-attempt authentication budget, two receiver workers, 16-command queue, 40-second
handshake budget, 256-byte frame bound, v1 wire format, PBKDF2 and AES-GCM remain unchanged.
Normal ACK waiting is bounded to five seconds, including a partially dribbled ACK; switching
the idle socket read to an infinite wait does not turn command waiting into an infinite wait.
Data-layer reads/crypto and blocking ACK waits remain off Main. No Activity/View reference
is introduced, and architecture boundary checks pass.

## Regression evidence

Nine new host-side regressions were added:

- ViewModel detects TV socket closure without a button press.
- Old receiver EOF cannot clear a replacement connection.
- Client observes idle EOF without transmitting input.
- Silent ACK expires rather than hanging.
- Byte-dribbled partial ACK expires rather than resetting the command budget.
- Local close interrupts an already pending ACK.
- Wrong-sequence ACK fails closed.
- Unsolicited authenticated replies retire the ghost connection.
- Repeated connection closure leaves no new reply-reader thread alive.

The entire targeted remote batch passed **32 tests**, including existing encrypted command
ordering, bad code, replay/tampering, oversized frame, handshake deadline, cancellation,
receiver stop and replacement coverage. The updated native lifecycle regression also waits
for EOF-driven failure without sending a button first; its Android APK compiles.

Failed intermediate attempts are not hidden:

- The first implementation passed functional tests but detekt rejected extra throw sites and
  wrapping only `ExecutionException.cause`. A shared connection guard and preservation of the
  full exception chain corrected this without broad new suppressions/baseline entries.
- The first thread-cleanup fixture tried ten pairings against an unchanged eight-attempt
  receiver budget and received EOF. It was corrected to six pairings, below the actual budget;
  production authentication limits were **not** weakened.

## Reviewed suspicions not counted as bugs

- The player uses `registerNetworkCallback`, not `requestNetwork`. A suspected invalid
  VALIDATED capability request was rejected: AOSP's listener path uses listenable capability
  validation, distinct from the request-only mutable-capability restriction.
  [AOSP ConnectivityService](https://android.googlesource.com/platform/packages/modules/Connectivity/+/0eccf35b6b1b123996ee41e4cc078cf79c35be89/service/src/com/android/server/ConnectivityService.java).
- TV receiver/server lock ordering was traced. `server.close()` does not acquire the worker's
  ownership lock or join its worker while the receiver lifecycle lock is held; no confirmed
  deadlock was found in that examined path.
- Player start/reattach, replacement, debounce cancellation, retry cancellation and release
  paths were inspected. No new reproduced playback bug is reported from this limited pass;
  manually calling a stale listener is not by itself evidence that Media3 can deliver it.

## Limits

At the end of the original bug-fixing pass, ADB listed no connected devices, so only the
Android instrumented test APK had compiled. A subsequent owner-authorized direct-Wi-Fi
pass ran the current APK on Xiaomi / Android 9: **all five native crypto/lifecycle tests
passed (140.407 s)**, including EOF-driven failure without a button press and successful
retry pairing. The current playback acceptance also passed separately (one test, 31.954 s).
Exact artifact hashes, native paths and remaining limits are recorded in
[XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md](XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md).
Samsung/Mi A2 have not executed this latest EOF revision in that follow-up.

A TCP black hole without EOF/RST cannot be identified instantly by this idle reader. It is
detected on a later command timeout. No claim is made that every Wi-Fi/power interruption is
proactively detected. Current native old-provider compatibility and retry pairing passed;
separate phone-to-TV network reconnection and prolonged control still need acceptance.
This is one reproduced remote-state bug, not a
whole-repository/no-more-bugs verdict; casting on Hisense VIDAA is a separate flow.

## Full regression gate

- `:app:verifyRoborazziDebug`: **2363** tests, zero failures/errors/skips; existing and
  newly added TV banner goldens verified without re-recording them.
- `:app:testReleaseUnitTest`: **2113** tests, zero failures/errors/skips.
- `:app:testPlayUnitTest`: **2113** tests, zero failures/errors/skips.
- Unchanged `:core:test`: **126** passing tests; Gradle reused the up-to-date result,
  rather than executing those core tests again during this pass.
- App/core detekt and `scripts/check-architecture-boundaries.sh` pass without adding
  baseline entries or relaxing the architecture gate.
- Debug/release lint: zero errors/warnings, one pre-existing target-SDK hint.
- Debug and Android test APK compilation pass. Native instrumented tests were not
  executed in the original build pass because ADB listed no devices; the later Xiaomi
  hardware acceptance is described above and was not part of this Gradle run.

The complete Gradle gate, including the signed release build, finished successfully
in 8m 28s. R8 retains its pre-existing async-provider warnings; no warning-free
toolchain claim is made.

## Rebuilt universal APK

The checksum below identifies the EOF-fix artifact. The subsequent HD banner build and
current checksum are recorded in
[TV_BANNER_HD_VERIFICATION_2026-09-30.md](TV_BANNER_HD_VERIFICATION_2026-09-30.md).

`app/build/outputs/apk/release/app-universal-release.apk`

- Version 0.9.7, universal versionCode 164, package `com.uacastplayer`.
- Size: 24,144,341 bytes.
- SHA-256: `97A5A780D247404EC110E3270AAF13F2B97AA8579F1621563105E7FA54B8DD96`.
- `apksigner verify --verbose`: verified v2/v3, one signer.
- minSdk 24, targetSdk 36; ARM32/ARM64/x86_64 libraries.
- New TV banner preserved; this artifact supersedes the artwork-only APK checksum.

No version bump, GitHub publication, device installation or computer shutdown was
performed in this bug-hunting pass. Unrelated existing worktree changes were preserved.
