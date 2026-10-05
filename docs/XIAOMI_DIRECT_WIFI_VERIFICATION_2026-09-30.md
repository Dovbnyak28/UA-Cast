# Xiaomi TV direct Wi-Fi acceptance — 2026-09-30

## Scope and installed artifact

After the owner connected the computer to the TV's Wi-Fi LAN, direct ADB access to
the previously authorized Xiaomi TV succeeded. No Samsung relay or USB forwarding
was used. The device is MiTV_MSSP2, Android 9 / API 28, 1280x720, ARM32.

Only `com.uacastplayer.debug` and `com.uacastplayer.debug.test` were updated with
`adb install -r`. Neither package was uninstalled and no application data was cleared.
The initial and final package inventories contain these two debug packages, not a
primary `com.uacastplayer` installation. No router/network settings were changed.

- Debug APK: `app/build/outputs/apk/debug/app-universal-debug.apk`.
- Version 0.9.7, versionCode 164; size 44,177,075 bytes.
- SHA-256: `BB645B0A15EC13DDC2CB12EA8971B1C37F1999C0F4BA266A9389F52F5BBA3337`.
- The installed `base.apk` was pulled and hashed: it matches the local debug APK exactly.
- Android test APK SHA-256:
  `4B3003C27A1F6B9FFD8DDF854D1A3FEE1A5F557BB1D352296C84EA1EAA6BB68A`.

The signed release APK was **not** installed in this pass. Its unchanged SHA-256 is
`97A5A780D247404EC110E3270AAF13F2B97AA8579F1621563105E7FA54B8DD96`.
Debug native acceptance is not a claim of executing the R8-minified release on hardware.

## Native remote tests

Command: `am instrument -w -r -e class
com.uacastplayer.remote.RemoteCryptoCompatibilityInstrumentedTest,com.uacastplayer.remote.RemoteLifecycleInstrumentedTest
com.uacastplayer.debug.test/androidx.test.runner.AndroidJUnitRunner`.

Result: **OK (5 tests), 140.407 seconds**. All five test completion codes were zero.

- Native providers produced the same 32-byte PBKDF2 wire key. Default BC derivation took
  12,873 ms; the explicit BC check took 12,642 ms. The security iteration count was unchanged.
- Activity background/foreground and recreation ended phone control without reconnecting
  automatically or accepting more commands from the disconnected owner.
- Connection replacement and receiver ViewModel destruction rejected stale commands and
  retired old sockets.
- A cancelled stalled handshake could not clear its replacement connection.
- **Receiver EOF was detected without any button press**. Phone state became retryable and
  disconnected; a new pairing then delivered NEXT successfully. This executes the strengthened
  regression for the latest fix, rather than the earlier send-to-discover-disconnection test.

These tests use native Android Main/lifecycle/crypto and real loopback TCP on the physical TV.
They do not reproduce a separate phone sending commands across the Wi-Fi network. Earlier
Samsung-to-Xiaomi real-LAN UI observations belong to the older artifact and are not relabelled
as current results. No phone was present in the current ADB inventory.

## Current native playback acceptance

The existing opt-in `TvPlaybackAcceptanceInstrumentedTest` ran with
`tvPlaybackAcceptance=true`: **OK (1 test), 31.954 seconds**.

Observed checkpoints:

- Owned temporary M3U imported through the real pipeline; two public AndroidX video samples.
- Focused channel opened with D-pad center.
- Rendered frames and advancing playback position, decoded video 1280x720.
- Pause/resume, background/foreground and Activity recreation passed with one player owner.
- Thirty immediate channel replacements: last channel won, one owner, moving video afterward.
- Back closed playback: Media3 IDLE, zero media items, no playing video/audio state.

The test refuses non-debug/non-TV/non-empty-source configurations. Its cleanup removed only
its temporary fixture, cancelled the pending import and restored the last-watched preference;
its empty-source-storage assertion passed. Human audible-audio confirmation was not performed.

## OEM launcher banner and a cache trap

The first HOME capture was the system screensaver, not a banner failure. After returning to
the launcher, it still displayed the old circular banner even though installation succeeded.
Merged manifest and APK inspection confirmed `@drawable/tv_banner`; the pulled installed APK
checksum matched the new artifact. One restart of the resolved HOME component's package,
`com.google.android.tvlauncher`, followed by HOME refreshed the cached drawable.

**No launcher storage, favorites or settings were cleared.** This is an observed launcher cache
issue after same-version debug replacement, not evidence that UA-Cast must clear launcher data
or that every normal versioned update has this behavior. Do not add system-launcher mutation
to application update logic.

Actual non-focused and focused TV tiles now show the new horizontal gold/white UA CAST name,
unified TV/play/cast mark and full-bleed navy/blue background. Both were visually inspected on
the physical 720p OEM launcher; no cropping or distortion was observed. Phone icons are unchanged.

Ignored local evidence: `app/build/device-audit/xiaomi-tv/direct-wifi-20260930/`:

- `remote-native-verified.txt` and `playback-native-verified.txt`.
- `launcher-home.png` records the stale cached tile.
- `launcher-reopened.png` and `launcher-uacast-focused.png` record the new tile.
- `installed-debug.apk` identifies the actual installed artifact.

## Limits and cleanup

This pass found no new application failure in the six selected native tests. It is not a
whole-repository/no-more-bugs verdict or TV certification. Remaining acceptance includes real
phone-to-TV control of the current artifact, network/IP loss and reconnection, prolonged
playback/control, provider/codec/audio coverage, OEM remote ergonomics and clean-install flows.
Hisense VIDAA DLNA is a different integration and was not tested here. TCP black-hole detection
without EOF/RST still depends on the next command's five-second acknowledgement timeout.

After testing, only the debug application/test processes were force-stopped. The owned device
screenshot copy was removed, local non-secret evidence retained, and the direct ADB connection
disconnected. No USB forward/relay was created. The updated debug installation and its existing
personal data remain. No production code edit, release publication, version bump or computer
shutdown was performed in this direct-Wi-Fi acceptance pass.
