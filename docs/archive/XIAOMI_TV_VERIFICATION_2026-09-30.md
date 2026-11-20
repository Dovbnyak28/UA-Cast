# Physical Xiaomi TV verification — 2026-09-30

## Scope

The owner authorized testing UA-Cast on the LAN Xiaomi TV and the connected Samsung phone.
ADB authorization eventually succeeded without bypassing the TV's authentication. Only the
isolated `com.uacastplayer.debug` application and its test package were installed/updated.
The primary app, system settings and the phone's saved playlists were not cleared or replaced.
Temporary loopback-only USB forwarding enabled the test computer to reach the TV through the
phone; the computer itself is on a different subnet. No router or TV-network settings changed.

TV: Xiaomi MiTV-MSSP2, Android 9 / API 28, ARM32, 1280x720, tvdpi. The device is old and has
limited memory; these results do not certify all Android TV devices or Hisense VIDAA.

## Confirmed problems and fixes

### Authentication deadline was incompatible with this real TV

The initial five-test native crypto/lifecycle run failed four tests: three connection waits
and one broken pipe while pairing. A native measurement then found PBKDF2-HMAC-SHA1 key
derivation took 12,993 ms with the default provider and 12,777 ms with the explicitly selected
BC provider. BC was the only available provider implementing this algorithm on this device.
The old five-second receiver handshake deadline closed legitimate connections before the
160,000-iteration computation could finish; a same-TV loopback needs two derivations.

`RemoteWire`, `TvRemoteServer` and `PhoneRemoteClient` now share a 40-second authentication
budget. The sender additionally owns an absolute timer to bound header/frame byte-dribbling;
it cancels the timer on completion and on close. TCP-connect and ordinary command timeouts
remain five seconds. The wire format/version, iteration count and AES-GCM have not changed.
Two workers, no pending executor queue, frame caps, authentication-attempt limits and receiver
stop cleanup remain in force. CPU derivation itself is not instantly interruptible; the socket
deadline and connection-generation guards still bound subsequent network/state application.

Only native test connection waits use the production authentication budget. Delivery,
cancellation, EOF and replacement assertions retain their original eight-second bounds.
New JVM tests prove that salt-dribbling cannot extend the sender's absolute deadline and that
closing a waiting handshake closes the socket and terminates the test worker promptly.

### TV navigation labels wrapped inside words

The real 720p display showed split labels and an abbreviated Settings destination. TV-only
rail width is now 184dp; full accessible destination labels occupy one line with an explicitly
full-width text layout. Phone width/labels are unchanged. The actual TV image was inspected;
the new Ukrainian 1.3-font-scale layout test rejects multi-line text, width overflow and
ellipsis rather than merely accepting that the strings are present.

## Verified results

| Check | Result |
|---|---|
| Xiaomi native crypto and remote lifecycle tests | OK, 5 tests, 118.893 s |
| Samsung phone UI/socket/lifecycle regressions | OK, 9 tests, 33.524 s |
| Real Samsung -> Xiaomi private-LAN pairing | Connected; TV pairing dialog closed |
| Real phone DOWN / OK | TV focus moved into search; OK opened the TV's own input keyboard |
| Real touchpad swipe / tap | Swipe moved TV focus from Home to Favorites; tap selected Favorites |
| Touchpad -> D-pad mode switch | Stayed connected; phone UP/OK then selected the TV Channels destination |
| TV leaving UA-Cast / receiver closure | Subsequent phone command produced the retryable disconnected state; no control of the system launcher |
| Physical-TV public-video / lifecycle / 30-switch acceptance | OK, 1 test, 38.315 s; both samples rendered advancing 1280x720 video |
| Full current JVM / screenshot / lint / detekt / signed build gate | Passed, 7m 24s |
| Architecture boundary script and diff whitespace check | Passed |

The complete gate contains 126 core, 2,350 Debug, 2,100 Release and 2,100 Play executions:
**6,676 executions, zero failures/errors/skips**, not 6,676 distinct test cases. The opt-in
network/physical-TV playback acceptance test was added afterward; its build/lint passed and
it passed separately, not included in that JVM count.
The rail layout test's literal labels were subsequently aligned to the actual current Ukrainian
destination names; the exact test and detekt were repeated successfully. This test-only adjustment
does not change the already-built APK or imply another complete JVM-suite execution.

### Native TV playback acceptance

`TvPlaybackAcceptanceInstrumentedTest` is explicitly opt-in (`tvPlaybackAcceptance=true`) and
refuses to run outside the isolated debug package, television configuration or an empty TV
source collection. It does not clear playlists or bypass onboarding. It imports an owned
temporary M3U through the actual controller/parser and opens its first channel with a focused
grid-card D-pad-center key. The two public H.264/AAC MKV/MP4 sample URLs come from the
[AndroidX Media demo inventory](https://raw.githubusercontent.com/androidx/media/release/demos/main/src/main/assets/media.exolist.json);
advertising/DRM URLs are not used.

Observed: rendered-first-frame events, positive decoded dimensions, `isPlaying` and at least
one second of advancing position; pause/resume; real Activity background/foreground;
Activity recreation retaining the same owner; 30 immediate channel replacements with the last
channel winning; moving video afterward; Back exit returning to the TV grid with Media3 IDLE,
zero media items and no playing audio/video. One player owner remained alive throughout.
The temporary import was cancelled, only its owned fixture was removed and the previous
last-watched preference restored. Native output: `playback-verified.txt` in local ignored evidence.
Audio tracks/decoding are not a substitute for a human audible-audio confirmation.

### Failed attempts are not hidden

- The first full gate failed the new rail geometry assertion, before the explicit text-width
  correction, and the existing `ProxyManifestLeaseTest` timed out after receiving 172 bytes.
  Its workers were idle; the accept thread was in native accept. All four targeted lease tests
  and the subsequent full suites passed without disabling tests or relaxing their timeouts.
  The intermittent lease-test failure's underlying cause is still unproven.
- The next complete gate passed the functional suites/build but failed detekt on one overlong
  diagnostic assertion line. Splitting the message, then repeating the full gate, passed.
- Some manual pairing-form actions initially used stale coordinates after the keyboard moved
  the dialog; no claim of application failure is made from those unsuccessful automation steps.
  Fields/focus were subsequently read before entry, and real pairing succeeded.
- The TV screensaver and system keyboard can make `uiautomator dump` fail to reach an idle
  state. Screenshots were used to distinguish this from a crash. Opening the input keyboard
  through the app works, but phone control is deliberately not a system-keyboard remote.
- The first two new playback-test runs imported successfully but did not render a frame.
  Added diagnostics proved that the request Flow had updated while the test-controlled Compose
  clock had not yet mounted PlayerHost (IDLE, zero media items, no decoder error). Waiting for
  Compose idle after the request corrected the harness; the complete acceptance then passed.
  No production playback-code change or relaxed frame/time assertions was made for this failure.

## Universal APK tested in this playback run

The TV launcher artwork was updated after this device run. The latest banner build
and its separate verification are recorded in
[TV_LAUNCHER_BANNER_VERIFICATION_2026-09-30.md](TV_LAUNCHER_BANNER_VERIFICATION_2026-09-30.md).
The checksum below identifies the playback-tested artifact, not that later build.

`app/build/outputs/apk/release/app-universal-release.apk`

- Version 0.9.7, versionCode 164; no version bump/publication during this test.
- Size: 24,138,720 bytes.
- SHA-256: `F16C96D57FCAA917A8B2E16908C9F94573EE06E3CB8AF12646E6B450A15517C5`.
- Signature verified: v2/v3, one signer; minSdk 24, targetSdk 36.
- ARM32 / ARM64 / x86_64 libraries; phone and Leanback launch activities present.
- Existing R8 async-provider warnings and lint's existing target-SDK hint remain; this is not
  a warning-free-toolchain claim.

Earlier artifact hashes/results in `ANDROID_TV_REMOTE_VERIFICATION_2026-09-30.md` are
historical and are superseded by the current artifact above.

## Remaining acceptance

Full provider-stream/codec and audible-audio acceptance, physical OEM remote ergonomics,
clean-install onboarding, TV permission/install windows, real Ethernet/Wi-Fi/IP reconnection,
prolonged playback and wider gesture/media-control scenarios still require separate evidence.
Do not treat passing loopback/Compose gesture tests as proof of all of these scenarios.

Ignored local evidence is under `app/build/device-audit/xiaomi-tv/`; it contains native test
outputs, provider timings and non-secret TV screenshots. Real pairing codes are not stored
in source, documentation or diagnostic logs.

## Cleanup

Both debug applications were force-stopped after acceptance, ending player and remote work.
Only the owned temporary device UI dumps/screenshot copies were removed; this includes files
that transiently held the pairing PIN. Non-secret pulled evidence remains in the ignored local
directory. The exact USB forward, localhost TV ADB connection and owned native relay process
were removed. A final process/forward inventory showed no remaining relay or forwarded port.
The installed debug APKs and personal data remain available; the primary package was unchanged.
