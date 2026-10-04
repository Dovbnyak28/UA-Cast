# Android TV banner anti-aliasing / HD resource — 2026-09-30

## Problem and evidence boundary

The owner reported visibly blocky edges on the Xiaomi launcher after the initial banner
redesign. The previous resource was a vector, not a small PNG that could simply be enlarged.
The earlier checks established the layout/resource binding and absence of cropping, but did
not establish that this legacy launcher's vector-to-tile conversion preserved edge quality.

A controlled before/after comparison used the same physical Xiaomi MiTV_MSSP2, Android 9,
density 213, focused card location and artwork. The new focused capture has visibly more even
letter/icon edges. This is a compatibility rendering improvement, not a claim of proving the
proprietary launcher's exact internal rasterization algorithm. Human panel feedback was
requested; no positive answer is assumed from the preselected UI suggestion.

`dumpsys display` reports a 1280x720 / 60 Hz Android UI mode as the only exposed mode on this
device. Increasing an asset cannot make that compositor draw a 1080p/4K interface. Display
size/density, TV output settings and launcher data/favorites were not modified.

## Scoped implementation

- `drawable/tv_banner_artwork.xml` retains the editable vector paths and original design.
- The source is rendered with native Android graphics into a 2560x1440 target and filtered
  down to a **640x360** opaque master. This is not upscaling an existing small raster image.
- `drawable-nodpi/tv_banner_hd.png` is the lossless shipped master: 49,592 bytes, SHA-256
  `F2B3627F2CB7B62F604E3F0457934D0E8C2A6B01863847D60350140186DF590F`.
- `drawable/tv_banner.xml` exposes a BitmapDrawable with explicit bitmap filtering, so the
  launcher receives a pre-antialiased bitmap rather than having to rasterize a vector.
- `nodpi` avoids reducing the source bitmap during resource decoding for the TV's density;
  the final card/focus scaling is filtered. Decoded source allocation is at most 921,600
  bytes / less than one MiB. The large generation target is used only by host-side tests.
- Phone launcher/adaptive icons, manifest bindings, UI layout, playback/remote logic,
  permissions and version numbers were not changed. Font attribution includes rasterization.

The 640x360 master retains the 16:9 ratio and meets the maximum standard banner density size
in the official [Android TV icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines).
It is deliberately decoded without a density reduction for legacy launcher compatibility;
this is not a claim of independent Play/OEM certification.

## Tests and failed intermediate gates

`TvLauncherBannerTest` now has six tests. They cover Leanback/activity/application resource
binding, unchanged phone icon, license packaging, 16:9 and opacity/safe margins, normal/small
launcher snapshots, retained 640x360 pixels at tvdpi, filtering and the one-MiB memory bound.
The generated master is compared to the packaged bitmap using **every ARGB pixel, zero tolerance**.

The first verification failed exactly the two existing banner snapshots after the intentional
anti-aliasing change. Their comparison images were inspected; only those two banner goldens
were re-recorded. No other application/phone screenshot was accepted or regenerated. A new
640x360 source-master golden was recorded separately.

The first full gate subsequently passed Debug but failed the new `Bitmap.sameAs` comparison
in Release. That object/native-bitmap comparison was replaced with exact visible ARGB arrays,
retaining dimensions, filtering, opacity, memory and screenshot assertions. The targeted
Debug and Release banner suites then passed all twelve executions; no pixel tolerance or
test exclusion was introduced. The complete gate was restarted afterward.

## Physical Xiaomi acceptance

Only `com.uacastplayer.debug` and its instrumentation package were updated with `install -r`;
no data was cleared and no primary application was installed or replaced.

Debug APK SHA-256:
`9E2D71BC5D60638A7940D17EBF0463B7576101CAAABB286913CA586C3AD71B46`.

`TvLauncherBannerInstrumentedTest.launcherLoadsFullResolutionFilteredBitmap` passed on the
actual Android 9 platform: **OK (1 test), 0.391 seconds**. It loads the activity banner through
PackageManager, asserts BitmapDrawable / 640x360 / filtering / memory / unchanged phone-icon
binding, renders a 160x90 tile and checks every pixel is opaque. No Activity or user playlist
mutation is involved.

The cached launcher resource was refreshed by restarting only the previously resolved HOME
package, without clearing its storage. Both non-focused and focused tiles were inspected.
Early screenshots taken during the HOME animation were black transitional frames, not crashes;
they were replaced with settled captures.

Ignored local evidence: `app/build/device-audit/xiaomi-tv/direct-wifi-20260930/`:

- `launcher-before-resolution-home.png`: focused vector-based banner before the change.
- `launcher-hd-unfocused.png` / `launcher-hd-focused.png`: actual updated OEM launcher.
- `banner-hd-native-verified.txt`: native test output.

Playback and remote logic were untouched. Their prior six native acceptance results belong to
the preceding debug artifact and are not relabelled as new executions in this graphics pass.
Other devices/launchers, production release runtime and human panel inspection are distinct
acceptance areas; no universal/no-more-bugs claim is made.

## Regenerating the master after an artwork change

Run only `:app:recordRoborazziDebug --tests
com.uacastplayer.ui.tv.TvLauncherBannerTest.hdBannerMasterIsGeneratedFromVectorSource`.
It renders `src/test/screenshots/tv_banner_hd_master.png` before the packaged-pixel assertion;
after a source edit that assertion intentionally rejects the stale packaged PNG. Copy the fresh
master to `src/main/res/drawable-nodpi/tv_banner_hd.png`, then verify the whole banner class.
Inspect differences before recording only `TvLauncherBannerTest.tvBannerAt*`. Never record the
entire UI suite to accept this one asset change. Keep the font attribution with derived assets.

## Final full regression gate and signed artifact

The restarted complete gate finished successfully in **8m 16s**:

- Debug `verifyRoborazziDebug`: 2,365 tests, zero failures/errors/skips; all goldens verified.
- Release unit tests: 2,115 tests, zero failures/errors/skips, including exact packaged ARGB pixels.
- Play unit tests: 2,115 tests, zero failures/errors/skips, including the same bitmap contracts.
- Unchanged core tests/detekt were up-to-date; the earlier 126-test core result was reused,
  not relabelled as a fresh core execution.
- App detekt and architecture boundary checks passed without expanding the baseline.
- Debug/release lint: zero errors/warnings; the pre-existing `OldTargetApi` Hint remains.
- Debug and native-test APKs built; the actual Xiaomi banner test passed as recorded above.
- R8 retains its two existing async-provider warnings; no warning-free-toolchain claim is made.

`app/build/outputs/apk/release/app-universal-release.apk`:

- Version 0.9.7, universal versionCode 164, package `com.uacastplayer`.
- Size: 24,171,240 bytes (26,899 bytes more than the preceding vector/EOF artifact).
- SHA-256: `E18FF9764F54B64A8BF12CE8C2CB66F600960BC4C603C6EC1FD103263E471F2D`.
- APK signature verification: v2/v3, one signer; minSdk 24, targetSdk 36.
- Post-R8 resource inspection confirmed the registered `drawable/tv_banner` points to a
  bitmap with `filter=true`, `dither=true` and the nodpi HD PNG. The actual optimized APK entry
  `res/Rg.png` is 640x360, 26,228 bytes. The HD resource was not lost during shrinking.

This checksum supersedes earlier local APK hashes; no already published release/tag was
overwritten. The signed primary APK was not installed during this pass. Only the isolated debug
artifact above was accepted on hardware; current release runtime is not claimed from that.

The owned native-test cache PNG and device screenshot copy were removed after read-only target
verification. Local non-secret evidence remains. Only debug/test processes were force-stopped,
then the direct ADB transport was disconnected; installations and user data were retained.
No version bump, GitHub publication, system resolution change or computer shutdown was performed.
