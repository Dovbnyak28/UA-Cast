# Android TV and phone remote verification — 2026-09-30

**Historical implementation report:** subsequent authorized physical-Xiaomi testing found and
fixed a slow-provider handshake timeout and TV rail label wrapping. Current device results,
remaining acceptance and the new APK hash are in
[XIAOMI_TV_VERIFICATION_2026-09-30.md](XIAOMI_TV_VERIFICATION_2026-09-30.md).
The unavailable-TV statements and APK hash below describe the earlier stage, not the current build.

## Scope and outcome

Implemented Android TV / Google TV presentation and an explicit **Remote / Touchpad** chooser
on the phone. The phone controls **only UA-Cast installed on Android TV**, as requested.
It does not control the TV system, other apps, a system keyboard or Hisense VIDAA.

The existing universal release artifact is version **0.9.7**, versionCode **164**. The version
was not bumped and nothing was published to GitHub during this implementation. Physical TV
acceptance is still outstanding; this is not a production-TV-certification claim.

Setup and limitations: [TV_SUPPORT.md](TV_SUPPORT.md).

## Implementation

- Manifest adds the Leanback launcher; the banner includes UA-Cast lettering. Touchscreen,
  Leanback and Wi-Fi remain optional, preserving phone installation and wired TV networking.
- TV uses a navigation rail, channel/favorite grid, enlarged text, overscan padding and visible
  focus rings. Playlist management uses existing controllers and persistence.
- TV player reuses the existing Activity-scoped PlayerViewModel/Media3 owner. D-pad/media input,
  overlay auto-hide, track/quality/EPG/sleep dialogs and exit cleanup are integrated with it.
- Single-line fields retain left/right text editing but use up/down to leave the field.
- Phone offers D-pad buttons or focus-based touchpad gestures, plus Back, play/pause, channel
  navigation and media volume. Changing the mode does not recreate the network connection.
- Manual pairing uses the TV's private IPv4 address, ephemeral port and eight-digit code.
  Automatic discovery and IPv6 are not implemented.
- Opt-in receiver binding, crypto and sockets live in `data.remote`; jobs/state belong to
  the `remote` ViewModels; input/presentation belong to the Activity and `ui.tv`/`ui.remote`.
  No data-to-UI or UI-to-ProxyServer dependency was introduced.

## Lifecycle and hostile input

The listener and phone connection close on backgrounding, Activity recreation and ViewModel
destruction. Generation/connection ownership and command-age validation reject stale queued
input. The Activity owns a dialog-window registry; dismissed dialog Views are unregistered.
Native input targets the top UA-Cast dialog before the underlying Activity.

Pairing expires after five minutes. AES-GCM frames authenticate sequence numbers; invalid
codes, tampering, replay, reordered/unknown commands and oversized frames fail closed. Limits
include two workers, no executor backlog, bounded command queues, a global attempt budget,
an absolute handshake deadline and idle connection expiry. Pending sockets close on stop.
Codes/keys/plaintext are not logged or persisted. This short-lived PIN-based LAN protocol has
not received an independent cryptographic assessment.

## Regression results

Final full gate, repeated after the large-font label correction, completed successfully in **5m 41s**:

| Suite | Executions | Failures / errors / skipped |
|---|---:|---:|
| core JVM | 126 | 0 / 0 / 0 |
| app Debug JVM / Robolectric / Compose | 2,347 | 0 / 0 / 0 |
| app Release JVM | 2,098 | 0 / 0 / 0 |
| app Play JVM | 2,098 | 0 / 0 / 0 |
| **Total** | **6,669** | **0 / 0 / 0** |

These are executions across variants, not 6,669 distinct test cases.

- `verifyRoborazziDebug`: passed, including the three new TV/remote goldens. The images were
  visually inspected. Existing phone screenshot baselines did not need replacement.
- `lintDebug`, `lintRelease`, app/core detekt: passed. Lint retains the pre-existing
  `OldTargetApi` **Hint** for targetSdk 36; there are no new lint errors/warnings. No baseline
  expansion was used to admit the new code.
- `scripts/check-architecture-boundaries.sh`: passed.
- `git diff --check`: passed.
- `assembleRelease -Puacast.requireSigning=true`: passed.
- R8 emits `Class file resource provider does not support async parsing` for AGP's
  `OrderedClassFileResourceProvider` during minification. This build-tool warning is retained;
  it is not a Kotlin/lint failure, and no claim of a warning-free Gradle build is made.

Forty new local test cases cover gestures, protocol abuse, socket ownership/cancellation,
TV search/favorite sorting, focus traversal, phone forms and native dialog input. Tests caught
and drove corrections to TV-dialog focus, search-field D-pad handling and callback ownership.

### Do not hide the first failed run

The first full gate encountered a five-second read timeout in the existing
`ProxyManifestLeaseTest` (1,000 segment fetches, zero response bytes). The dump showed idle
response/admission workers and the accept thread in `accept()`. It did not establish a new
production-code defect. The exact test passed twice in targeted runs and then in the complete
rerun. Its timeout/assertions were not weakened and it was not disabled. The intermittent
timeout's cause remains unproven; a future recurrence needs connection/OS-level diagnostics.

## Samsung instrumented verification

Samsung **SM-M156B**, Android **16**, was available after the full gate. Tests install only
`com.uacastplayer.debug` and its test runner, not the primary package. Test content is isolated
Compose UI and loopback traffic; no user playlist fixtures or actual TV commands are involved.

All five scheduled device tests passed according to the UTP `test-result.textproto` and
execution log: application launch, mode chooser, pairing form/keyboard, actual touch gestures,
and authenticated Android crypto/socket delivery of every whitelisted command.

The first installation encountered an old test-runner signature mismatch. The following
UTP run executed all five tests successfully, but Gradle still reported failure because the
host-side gRPC result channel was unavailable. This infrastructure result is distinct from
the device test results. The direct AndroidJUnitRunner rerun then returned **OK (5 tests)**
in **13.881 seconds**, independently confirming the result without that host transport.

Evidence: `app/build/device-audit/remote-control/instrumentation.txt`; four phone screenshots
are retained under `app/build/device-audit/remote-control/remote-control-verification/`.
The device controls test renders the full actual remote dialog, including mode switching,
scrolling and its real surface colors, rather than a bare test container. Screenshots of the
chooser, pairing form, D-pad and touchpad were visually inspected. The Samsung's larger font
exposed a mid-word wrap in the original channel button label; compact channel −/+ labels now
fit, while accessibility retains the full previous/next channel descriptions in all four locales.
The debug package is stopped after testing. The primary app was not installed, uninstalled,
cleared or modified. The instrumented-test APK build and updated `lintDebug` also passed.

### Additional native lifecycle verification

After the user confirmed that the phone remained connected, four additional instrumented
tests were added in `remote/RemoteLifecycleInstrumentedTest.kt`. The complete targeted device
run returned **OK (9 tests)** in **34.462 seconds** on the same Samsung / Android 16.

| Native Android scenario | Observed result |
|---|---|
| Actual MainActivity background / foreground and Activity recreation | Connection closes; the retained ViewModel stays disconnected and does not reconnect automatically |
| Cancel a socket waiting for a handshake, immediately connect to a new receiver | Old socket reaches EOF; its cancelled job does not clear the replacement connection; SELECT arrives once |
| Receiver closes the socket, sender tries a command, then retries with a new receiver | Retryable failed state, not connected; subsequent NEXT arrives at the replacement |
| Replace the controlling client and destroy the TV receiver ViewModel | Old connection's commands are rejected independently of their age; current command applies once; destruction invalidates queued input and closes the socket |

These tests use real Android Main dispatching, crypto and loopback sockets. The Activity is
the actual MainActivity, not a simulated lifecycle. They do not load playlist fixtures,
clear preferences or contact a real TV. The primary package was not changed.

The first new lifecycle-test run failed JUnit initialization because an expression-bodied
test inferred a non-void return type. Explicit `Unit` returns corrected the test harness;
the subsequent complete nine-test run passed. No production-code change was necessary.

`assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, detekt, architecture boundaries and
`git diff --check` passed. The release APK hash remains unchanged. The test-only change does
not imply that the earlier 6,669-execution full gate was rerun during this additional check.

Evidence: `app/build/device-audit/remote-control-followup/instrumentation-verified.txt` and
the retained phone images in the adjacent `remote-control-followup/` directory. The debug
application is stopped after testing. Full phone-to-Android-TV acceptance remains outstanding.

## Xiaomi TV discovery follow-up

The owner subsequently offered a Xiaomi TV on the LAN. Read-only discovery from the Samsung
identified **MiTV-MSSP2** advertising Google Cast and Android TV Remote services. The production
SSDP discovery returned zero DLNA renderers in this search window; that is an observed discovery
result, not proof that the TV can never support DLNA.

The computer and phone are on different IPv4 subnets. A direct computer-to-TV connection timed
out. A temporary loopback-only ADB forward through the USB phone reached the TV's existing ADB
listener, but the TV returned **unauthorized**. One reconnect produced the same result. The owner
reported that no authorization dialog appeared on the TV. Authentication was not bypassed and
no APK, settings, stored playlists or playback on the TV were changed.

An explicitly opt-in `LanTelevisionProbeInstrumentedTest` performs this read-only SSDP/mDNS
inventory from the phone. It skips in ordinary device-test runs unless `lanTelevisionProbe=true`
is supplied. The diagnostic run returned **OK (1 test)** in **13.618 seconds**; this means the
inventory finished, **not** that TV playback or phone-to-UA-Cast control passed. The test APK
build, `lintDebug`, detekt and `git diff --check` passed. No production source was changed for
this discovery. The diagnostic debug application was stopped afterward.
Both temporary bridge helpers were closed; the USB forward and unauthorized ADB transport
were removed. The release APK hash remains unchanged.

Local evidence: `app/build/device-audit/xiaomi-tv/phone-lan-probe.txt`. Device names, local
addresses and transient bridge details are retained only in the ignored local diagnostics.
Physical-TV acceptance remains blocked until the owner enables/approves ADB on the TV or
manually installs UA-Cast and provides the application's pairing address/code.

## Universal APK

Path: `app/build/outputs/apk/release/app-universal-release.apk`

- Size: **24,122,336 bytes**.
- SHA-256: `D6896384582EA7721F17B824C1964BEE16227D2F9BFAD71A211EE9A6BCD6B85A`.
- `apksigner verify --verbose`: verified v2/v3, one signer. v1 is unnecessary for minSdk 24.
- `aapt dump badging`: minSdk 24, targetSdk 36, phone and Leanback launchers present;
  `armeabi-v7a`, `arm64-v8a`, `x86_64` native libraries present.

## Remaining acceptance before a TV release

1. Install on an actual Android TV / Google TV or a TV system image; verify launcher/banner,
   first-run language/Terms, OEM D-pad, scrolling and large-font layouts.
2. Play real provider streams; verify codecs/audio, channel replacement, background/return,
   exit cleanup and TV-specific decoder behavior.
3. Pair a real phone with a separate TV over Wi-Fi and over TV Ethernet + phone Wi-Fi.
   Test disconnect, re-pair, code expiry, network/IP changes and a slower TV's handshake cost.
4. Verify TV keyboard, permission and APK-install system windows with the physical remote.
   Phone app-only control deliberately cannot operate those system windows.
5. Assess advanced phone-feature parity on the TV, including group management. Shared parental
   and hidden-group policies are retained, but a TV grid is not the phone's complete gesture UI.

No Android TV image or TV ADB device was available. Passing phone/loopback/Robolectric tests
is useful evidence, not a replacement for those checks.
