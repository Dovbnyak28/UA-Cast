# Xiaomi TV stabilization check — 2026-10-03

## Outcome: acceptance is incomplete

The owner confirmed that Mi TV was connected and had previously authorized testing the
isolated debug application on this TV. The TV was discovered, ADB authorization succeeded,
and the current debug APK was installed and independently matched by checksum. Native
acceptance did **not** complete: the first run encountered an active system screensaver;
the unchanged rerun lost its network/ADB transport. Do not count either run as a passing
17-test TV regression or as proof that playback or real phone-to-TV control works.

No production source, assertions, timeouts, screenshot baselines, application version or
release publication changed in this pass. The earlier successful host/phone verification
belongs to [PHONE_SEARCH_CODE_POLISH_2026-10-03.md](PHONE_SEARCH_CODE_POLISH_2026-10-03.md),
not to the incomplete physical-TV run below.

## Device and installation boundaries

- Actual authorized ADB identity: Xiaomi `MiTV_MSSP2`, product/device `volver`, API 28,
  `armeabi-v7a`; the observed UI is 1280×720.
- The computer and USB Mi A2 were on different IPv4 subnets. Read-only SSDP/mDNS inventory
  from the phone found the TV's current address, different from the historical address.
- Only an ephemeral PC localhost forward to a phone loopback listener was used to reach
  the TV's existing ADB listener. No router, Wi-Fi, TV developer settings or ADB keys changed.
  Authorization was not bypassed. Failed host-side stream-relay attempts were closed.
- Initial TV package inventory had `com.uacastplayer.debug.test`, but neither
  `com.uacastplayer.debug` nor the primary `com.uacastplayer` package. The debug app was
  therefore a fresh installation, not an update of existing TV playlist data.
- Only the debug application and its test package were installed with `adb install -r`.
  Nothing was uninstalled or cleared. No primary app was installed or modified.
- The selected tests use isolated Compose content or native loopback sockets. No provider
  playlist import, PIN reset, purchase, diagnostic transmission or live renderer command ran.

## Exact artifacts

| Artifact | Size / identity | SHA-256 |
|---|---|---|
| Universal Debug APK | 44,247,147 bytes; 0.9.7 / 164; `com.uacastplayer.debug` | `6FB95894F8E4841C8C9CE06F5006DB0DCCE2F1ECE990C4EB768A058566577E72` |
| Android test APK | 1,407,923 bytes; `com.uacastplayer.debug.test` | `4830D331E9B84E8FCFBFFF68478E1F044BB61D1AF6F4FEB2B26BA32DA61FAC55` |

The installed TV `base.apk` was pulled into ignored local evidence and its SHA-256 matched
the Universal Debug APK exactly. The signed/minified Release APK was **not** installed or
executed on the TV in this pass.

## Failed run and actual evidence

The first AndroidJUnitRunner run selected these classes:

- `TvLauncherBannerInstrumentedTest`: 1 test.
- `TvConfirmationDialogInstrumentedTest`: 8 tests.
- `TvEmptyModalInstrumentedTest`: 3 tests.
- `RemoteCryptoCompatibilityInstrumentedTest`: 1 test.
- `RemoteLifecycleInstrumentedTest`: 4 tests.

Result: **17 tests, 11 failures, 236.524 seconds**, not a pass.

Ten UI failures reported `No compose hierarchies found`; the Activity lifecycle test also
found a still-connected state after asking the scenario to move to CREATED. During this
run, read-only platform diagnostics showed `mWakefulness=Dreaming`, `mShowingDream=true`
and current focus on `Sys2023:dream`, not on UA-Cast. The prerequisite of an actually visible,
resumed Activity was therefore not established. Do not classify the lifecycle assertion
as a demonstrated production disconnect bug without repeating it with that prerequisite.
The current MainActivity explicitly disconnects both remote owners in `onStop()`; the
Compose remote host also handles ON_STOP. Neither was edited to hide the failure.

The six successful tests in this failed run included the launcher drawable check, native
crypto compatibility, three other socket/ownership tests and one empty-modal routing case.
BC native key derivation measured 12,821 ms by the default path and 12,553 ms with the
explicit provider. The 160,000 security iterations and authentication deadline were unchanged.
Filtered AndroidRuntime error output for the selected debug process contained no crash.
This limited log observation is not a whole-device/no-crashes guarantee.

After the run, ordinary WAKEUP/HOME input ended the screensaver. Read-only diagnostics
then confirmed `Awake`, `mShowingDream=false` and current focus on UA-Cast MainActivity.
The language screen was captured and visually inspected: four readable language choices,
a visible D-pad focus ring and a disabled Continue action before selection. D-pad input
was attempted, but the next UI hierarchy capture returned a null root; its stale file was
not used as evidence that navigation or onboarding had completed. The cold `am start -W`
also reported a wait timeout before the actual visible screen was observed. Neither
observation proves a crash or a successful complete first-run flow.

## Rerun interrupted by transport loss

Exactly the same 17 classes, APKs, assertions and timeouts were scheduled after the awake
check. ADB then returned `error: closed` and the TV transport became offline, without a
complete JUnit result. That run is **interrupted/unverified**, not passing or an attributed
application failure.

Subsequent read-only checks from the phone established:

- The phone still had its original Wi-Fi route and could ping its router.
- The TV's last observed address no longer answered ping.
- A second mDNS inventory no longer found its Google Cast advertisement; its Android TV
  Remote service could not resolve within the bounded search window.
- Another Chromecast remained discoverable on the same LAN.

These observations establish loss of TV reachability, but do not distinguish TV power-off,
TV network loss or an address change. Do not invent a cause such as an app-triggered TV crash.
The owner was asked to confirm that the TV remained powered and connected.

## Evidence and cleanup

Ignored local evidence is under `app/build/device-audit/xiaomi-tv/`:

- `native-regression-20261003.txt`: the full 17-test/11-failure first result.
- `native-regression-awake-20261003.txt`: interrupted ADB output, no complete test result.
- `installed-debug-20261003.apk`: installed artifact identity.
- `onboarding-20261003.png`: non-secret first-run screen.

Transient LAN addresses, relay helpers and any private diagnostics stay in ignored local
evidence, not public source. No pairing PIN was obtained, persisted or logged. The owned
localhost ADB transport and exact USB forward are removed; the phone debug app is stopped
and its owned temporary UI dump removed. Closed host relay processes do not remain active.
The updated debug APKs remain installed on the TV. The TV's temporary non-secret screenshot
and UI-dump copies cannot be removed while it is unreachable; remove those exact owned
paths when connectivity returns, not arbitrary storage or user data. There was no computer
shutdown or TV reboot.

## Required continuation

1. Restore reachability and verify actual TV identity, Awake state and UA-Cast foreground focus.
2. Check for any unfinished instrumentation, stop only the isolated debug/test processes if
   necessary, then repeat the unchanged 17-test suite to a complete JUnit result.
3. Complete the actual D-pad language/Terms flow without bypassing onboarding or importing
   over user sources. Confirm the debug TV source collection is empty.
4. Run opt-in `TvPlaybackAcceptanceInstrumentedTest` with `tvPlaybackAcceptance=true`:
   real decoded/moving video, pause/resume, recreation, 30 replacements and Back/IDLE cleanup.
5. Pair the current Mi A2 and TV builds over the real LAN; verify D-pad, touchpad, mode switch
   and disconnect on backgrounding. Loopback success cannot replace this acceptance.
6. Check OEM launcher rendering, audible audio, real provider streams and prolonged operation
   separately. Xiaomi Android TV acceptance is not Hisense VIDAA DLNA acceptance.

There is no new confirmed application defect from this incomplete pass, and no basis for an
"everything fixed" or a release-ready verdict. Device acceptance must continue after access
is restored.
