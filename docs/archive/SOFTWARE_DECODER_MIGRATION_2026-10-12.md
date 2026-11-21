# Apache/LGPL software decoder migration — 2026-10-12

## Result

NextLib has been removed from the application's runtime dependency and renderer
factory. UA-Cast now depends on :ffmpegdecoder and Media3 discovers its software
audio/video renderers. Hardware remains first; software decoding has not been
removed as a licence shortcut. Own application code is not relicensed under GPL.

The replacement wrapper is Apache-2.0; independently compiled FFmpeg 6.1.6 is
LGPL-2.1-or-later, with GPL/nonfree/version3 disabled. Native VP8/VP9 are compiled
without importing any NextLib AAR code/binary. Provenance and modifications are
in ffmpegdecoder/ORIGIN.md and native-sources.json.

## Build and publication protection

- scripts/prepare-software-decoder.py verifies complete source-archive hashes,
  pinned NDK/SDK CMake, source paths, ELF ABI/type/program headers and 16-KiB
  alignment. Builds are bounded, isolated from previous extractions and published
  with a source/library SHA-256 manifest only after all three ABIs succeed.
- Gradle verifySoftwareDecoderNative blocks missing manifests, changed inputs,
  corrupted libraries, wrong ABIs, partial output sets and extra wrapper libraries.
  Packaging requires five native libraries in each of arm64-v8a, armeabi-v7a and
  x86_64. Media3 version changes also require refreshing the tested wrapper.
- Seven negative Python tests cover corrupted archives, traversal, wrong/truncated
  ELF headers, 4-KiB alignment, stale source/binary hashes, missing ABIs/manifests
  and an extra libmedia3ext.so.
- A reusable native-build workflow prepares verified inputs for CI, signed-release
  and performance jobs. Emulator matrices also run the isolated software suite.
  Workflows parse successfully, but **remote CI has not been run for these changes**.
- scripts/package-software-decoder-source.py produces a whitelist-only bundle:
  complete FFmpeg/libyuv source archives, wrapper sources/licences, build-only
  patches, actual per-ABI configurations, binary/source hashes and rebuild/relink
  instructions. It excludes private keys, logs, playlists and unrelated web files.
- Signed-release compliance remains fail-closed. Only decoder migration is approved;
  source publication/relink acceptance and other legal prerequisites remain open.

Local source bundle: build/software-decoder-dist/uacast-software-decoder-source.zip.
It must accompany an eventual APK publication; an ignored local ZIP is not proof
of public distribution compliance. Never publish production signing keys.

## Verification

Mi A2 / Android 11/API 30:

- **28/28 main-app tests**, 103.862 s: production renderer wiring, local-file
  playback, player lifecycle, screen/controls/aspect-ratio regression.
  The protected runner restored all original debug files/preferences and verified
  **three original private file hashes**. The release app/data were not replaced.
- **15/15 isolated native tests**, 10.08 s: exact H.264/MPEG-2 B-frame/EOS counts,
  VP8/VP9, Surface rendering, MP2/AC-3/E-AC-3/DTS PCM counts, native licence,
  failure/flush/release ownership, actual Media3 playback and 30 replacements.
- **1/1 SDK-only minified JNI probe** in com.uacastplayer.debug.minified:
  JNI_OnLoad registered all native classes/method descriptors after R8 and
  avcodec_license() returned LGPL version 2.1 or later. This is a linkage/licence
  probe, **not full minified playback/UX acceptance**.

Full local gate:

- Debug **2743**, Release **2335**, Play **2335** unit tests: zero failures/errors/skips.
- **54 existing screenshot goldens** verified; none were replaced to hide a change.
- App lint Debug/Release/Play: zero errors/warnings, two existing hints each.
- App detekt and core tests passed; release APK and Play AAB shrinking/packaging passed.
- Native buffer tests 5/5; release compliance guard tests 9/9; legal HTML synchronized.

Evidence (ignored): migrated-mi-a2-main-regression.log,
migrated-mi-a2-native-direct.log, mi-a2-minified-native-probe.log,
non-gpl-main-release-gate-7.log, native-bootstrap-3.log and
non-gpl-final-small-gate.log under build/legal-audit.

Release APK inspection found all 15 FFmpeg/JNI libraries, no libmedia3ext.so,
no old NextLib DEX symbols, all wrapper/FFmpeg/libyuv/NDK attribution assets and
no internal legal-readiness JSON. JNI buffer class identities are retained in R8
mapping; keeping SimpleDecoderOutputBuffer is necessary for native audio descriptors.

## Additional failures investigated, not hidden

- Six screenshot-harness failures were missing ignored build/ diagnostic images,
  not failed control geometry. Only the reviewed UK image remains a golden; all
  locales retain the same strict bounds/minimum-size assertions. Fresh CI no longer
  depends on generated files from a prior developer run.
- Windows Gradle could not fetch a newly selected pinned annotation JAR because
  Java's TLS trust did not match system HTTPS trust. Exact Maven files were fetched
  with validated system HTTPS/checksums, through an ignored annotation-only local
  mirror/init script. Dependency versions and CI repositories were not changed;
  TLS verification was never disabled. The Maven version publishes SHA1, not SHA512.
- Gradle UTP did not return test results on this Windows/device run. Direct
  instrumentation ran the same 15-test suite successfully; the UTP failure is retained.
- AndroidJUnitRunner could not bind shared Trace/Kotlin facade classes to a
  release-minified host. A small Android-SDK-only runner performs the native linkage
  probe without retaining broad Kotlin/AndroidX packages in production.
- Compiler-only Error Prone metadata required one exact test-only R8 dontwarn rule;
  no production javax.* or global native warnings were suppressed.

Nonblocking build diagnostics remain: R8's AGP async-provider warning and local
AGP inability to strip the independently built JNI library without a SDK-installed
NDK. These are not reported as fixed. Native symbol/size polish is a follow-up.

## Still required before publication

1. Remote Linux CI plus API 24/30/36 emulator suites; actual armv7/x86_64 runtime
   checks and broader minified playback. Local physical runs selected arm64.
2. Software 1080p/4K CPU/heap/battery budgets, HEVC/additional audio, HDR/rotation,
   long sessions, adversarial seek/adaptive streams and malformed-media stress.
3. Publish exact corresponding source with APK, validate replacement-library
   relinking/install instructions, show/publicly link proper attribution.
4. Resolve previously published GPL-wrapper APK obligations separately; the new
   dependency does not retroactively cure prior distribution.
5. Artwork provenance, EPG provider permissions/user-source policy, canonical public
   privacy deployment, top-level Cast entry point and publisher/seller legal review.

No GitHub release, version bump, production signing-key change or blanket legal
certification was performed. The build is **not approved for publication yet**.
