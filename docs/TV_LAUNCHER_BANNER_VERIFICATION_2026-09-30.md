# Android TV launcher banner — 2026-09-30

This report describes the initial vector redesign. A subsequent owner-reported pixelation
follow-up changes the shipped resource to a filtered, supersampled HD bitmap while retaining
the editable vector. Current implementation, checks and artifact:
[TV_BANNER_HD_VERIFICATION_2026-09-30.md](TV_BANNER_HD_VERIFICATION_2026-09-30.md).

## Scope and design

The supplied Xiaomi launcher photos showed a small stroked name at the bottom,
a circular backing plate and separated cast/play elements. Only the dedicated
`app/src/main/res/drawable/tv_banner.xml` artwork was redesigned:

- Full-bleed 16:9 navy-to-blue background; no additional frame or baked-in focus effect.
- Unified TV/play/cast mark, with a gold play symbol and cyan cast waves.
- Centered horizontal UA CAST wordmark: filled bold contours approximately 37 px high
  in the 320x180 viewport, replacing the old approximately 18 px stroked lettering.
- High-contrast gold/white name, with safe margins and no extra tagline/badges.
- Vector geometry instead of a raster image or runtime text/font lookup.

The phone launcher/adaptive/monochrome icon files, application icon reference,
playback logic, remote-control protocol, manifest and permissions were not changed
by this banner task. No version bump or GitHub release was made.

The brand name is the same in all application languages, so no translated copy is
baked into the banner. Letter contours derive from Android's Roboto Condensed Bold;
the original font's name-table metadata identifies Copyright 2011 Google Inc. and
Apache 2.0. Attribution, modification notice and the full license are packaged at
`assets/licenses/tv-wordmark-roboto.txt`; the font binary/generator is not shipped.

The composition follows the 16:9/name-in-banner guidance in the official
[Android TV icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines).
This is not a claim of OEM/Play TV certification.

## Verification

`TvLauncherBannerTest` loads the banner through the application's PackageManager,
as a launcher does. Native Android rendering, not a separate SVG/mock-up, produces:

- `app/src/test/screenshots/tv_launcher_banner.png` — 320x180.
- `app/src/test/screenshots/tv_launcher_banner_small.png` — 160x90.

Both renders were inspected. The tests verify Leanback resolution, the dedicated
banner, unchanged phone icon resource, 16:9 intrinsic ratio, opaque background,
foreground safe margins, retained foreground after downscaling and packaged attribution.
The two new goldens were recorded separately; existing phone/application goldens
were not regenerated. Subsequent verification reported zero changed pixels.

The full debug `verifyRoborazziDebug` run passed **2353 tests**, zero failures/errors/
skips. After the attribution asset/test and a comment-only resource update, all four
banner tests were repeated successfully in debug, release and Play variants (12 test
executions). This is not a claim of rerunning the complete release/Play suites.

Debug/release lint and detekt passed. Lint retains the pre-existing target-SDK hint;
the release build retains R8's pre-existing async-provider warnings.

## Initial physical-device limit and subsequent acceptance

The Samsung USB connection disappeared while re-establishing the previously authorized
LAN bridge to Xiaomi. ADB then listed no devices. The relay ADB session exited and
the forward inventory was empty. No debug or primary application was installed or
updated on either device during the initial banner task. At that point the new banner
had not been observed on the physical Xiaomi launcher; earlier playback/remote acceptance
did not substitute for that check.

Install the newly built APK to see the new tile. An already installed APK still
contains the old banner; screenshots of it are not evidence of the new resource.

The owner subsequently connected the computer to the TV's Wi-Fi LAN. The current debug
APK was installed on Xiaomi / Android 9 without clearing application data, and its installed
checksum was verified. The OEM launcher initially retained the old banner after the same-version
replacement. Restarting only `com.google.android.tvlauncher` (without clearing its storage,
favorites or settings) refreshed the tile. The new banner was then inspected in both normal
and focused states on the actual 720p launcher: the name/mark were readable and uncropped.
Native remote and playback tests also passed (5 + 1 tests). Evidence and exact scope:
[XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md](XIAOMI_DIRECT_WIFI_VERIFICATION_2026-09-30.md).

## Final APK

This is the artwork-only artifact. Subsequent remote-state fixes rebuild the APK;
the later checksum/verification is recorded in
[REMOTE_EOF_STABILIZATION_2026-09-30.md](REMOTE_EOF_STABILIZATION_2026-09-30.md).

`app/build/outputs/apk/release/app-universal-release.apk`

- Version 0.9.7, universal versionCode 164, package `com.uacastplayer`.
- Size: 24,144,341 bytes.
- SHA-256: `2354251EC906D3009A9CD25CF8D39174E86BA2374E0EB9A4998291D03574E2F6`.
- `apksigner verify --verbose`: verified v2/v3, one signer.
- minSdk 24, targetSdk 36, ARM32/ARM64/x86_64 libraries; Leanback launch activity present.
- The APK ZIP contains the wordmark attribution/license asset (11,781 bytes).

This artifact supersedes the earlier artwork/checksum, not the limitations of the
physical playback/remote tests in `XIAOMI_TV_VERIFICATION_2026-09-30.md`. The later direct-Wi-Fi
report above records OEM launcher acceptance of the current debug APK; the signed release
APK was not installed during that hardware follow-up.
