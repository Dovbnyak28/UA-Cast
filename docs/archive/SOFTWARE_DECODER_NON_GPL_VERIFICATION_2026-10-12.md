# Software-decoder replacement — verified staging, 2026-10-12

This is the earlier staging snapshot. The completed local runtime migration and
latest verification are recorded in SOFTWARE_DECODER_MIGRATION_2026-10-12.md.

## Status, deliberately separated

The independent replacement has passed local builds and real-device codec tests.
It is **not yet connected to UA-Cast's runtime**: PlayerRenderersFactoryProvider
still imports NextRenderersFactory and app/build.gradle.kts still depends on
NextLib. No GitHub release or legal-compliance approval was issued.

The user's requirement is retained: do not remove software audio/video decoding
as a shortcut for removing the GPL wrapper. The application's own code has not
been granted a GPL licence. Apache-2.0 applies to the staged decoder wrapper;
FFmpeg remains a separate LGPL component with corresponding-source/relinking
obligations. Replacing a future dependency alone does not cure obligations for
previously distributed APKs.

## Independently built components

- Apache-2.0 wrapper based on AndroidX Media contribution #1591, immutable commit
  `ddf578a27a97e6296062d1997ba41784b0dcf497`. Original headers and a local-change
  inventory are retained in ffmpegdecoder/ORIGIN.md. This is not an official
  upstream production release of the video renderer.
- Official FFmpeg 6.1.6 sources, SHA-256
  `d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a`.
  GPL/nonfree/version3 and external dependencies are disabled. Audio/video
  decoders are compiled independently; no NextLib AAR Java/JNI/binaries are reused.
- BSD-3-Clause libyuv at `eeb06a6451ba2e21f8ff272c7b42fc62b9faecc5`;
  archive hash and Chromium upstream reference are in native-sources.json.
- Android NDK r26b (26.1.10909125), CMake 3.22.1, Android minimum API 24.
  FFmpeg and JNI are built for arm64-v8a, armeabi-v7a and x86_64.
- FFmpeg is dynamically linked. Licence texts, wrapper attribution, libyuv
  attribution and the NDK runtime NOTICE are included in the module assets.

Runtime `avcodec_license()` is tested, rather than inferring the native licence
from the wrapper's POM: **LGPL version 2.1 or later**.

## Bugs repaired in the candidate before integration

1. SimpleDecoder's one-input/one-output video model did not drain delayed frames
   at EOS. Replaced it with bounded pools and explicit receive/send/EOS handling.
2. EAGAIN packets could be copied without FFmpeg padding, or retained in an
   unbounded queue. Inputs now remain owned in a bounded pool until accepted;
   padding is capacity-checked and zeroed in JNI.
3. Native extradata padding was uninitialized. Allocation now uses av_mallocz.
4. Failed constructors could leave a SimpleDecoder worker alive; native video
   initialization failure also leaked its JNI context. Failure paths clean up.
5. Interrupted join could permit freeing a native context still used by a worker.
   Release now waits for worker termination and preserves interruption status.
6. Audio resampling confused samples-per-channel with bytes/capacity and exposed
   the capacity rather than actual written bytes. Corrected counts and offsets.
7. Frame conversion did not handle allocation failures and could retain a freed
   cached swscale pointer. Corrected ownership and failed allocation handling.
8. JNI direct-buffer sizes, output dimensions and plane lengths were insufficiently
   checked; additions/multiplications now use bounded/wider arithmetic.
9. Color-space metadata could remain stale at unchanged dimensions; initialized
   per frame. Surface failure and global-reference acquisition clean up correctly.
10. Frame timestamps, rather than triggering-packet timestamps, control output
    filtering. Flush generations discard outputs decoded before a flush.
11. MPEG layer I/II map to their actual built-in decoders. Trace sections close
    on initialization exceptions; reflection/JNI members have scoped keep rules.
12. Windows make truncated long armv7 linker/header commands. A documented,
    build-only response-file/header-list patch is supplied with the sources.

These findings concern the **candidate**, not proof that every bug existed in the
currently installed UA-Cast NextLib implementation.

## Verification evidence

Mi A2, serial 263efaf, Android 11/API 30. Separate test-only package:
`androidx.media3.decoder.ffmpeg.test`; installed UA-Cast release/debug data were
not cleared or replaced. Play Protect initially rejected the test harness's
missing target SDK; testOptions.targetSdk=36 corrected the cause. No protection
was disabled or bypassed.

- **15/15 instrumented tests**, 10.21 seconds, no skips:
  H.264/MPEG-2 B-frame ordering and exact 30-frame EOS counts; VP8/VP9 decoding;
  Android Surface rendering; exact MP2/AC-3/E-AC-3/DTS PCM sample counts; native
  capabilities/licence; failed audio initialization; 30 flush/release cycles;
  actual Media3 extraction/rendering/EOS; 30 rapid media-item replacements;
  exactly one software audio/video renderer discovered by DefaultRenderersFactory.
- **5/5 decoder-buffer unit tests**: zero padding, alias independence, bounded
  initialization data, oversized packets, invalid padding.
- **2/2 privacy-navigation unit tests**: reviewed language anchors allowed;
  other URLs/files/content/script navigation rejected.
- **9/9 release-guard Python tests**: missing evidence, forbidden runtime
  dependencies, malformed types and unapproved commerce fail closed.
- Existing app regression: Debug **2743**, Release **2335**, Play **2335**,
  zero failures/errors/skips; core tests up-to-date. Build finished successfully.
- Main app debug build, lint and detekt passed. Decoder lint has zero errors;
  nonblocking pinned-version/catalog warnings remain. Aligning the module's
  Kotlin model eliminated lint's incompatible-metadata diagnostics.
- APK privacy/terms contents exactly match generated HTML. Internal readiness
  and policy-source JSON are outside APK assets.

Test APK SHA-256 (also verified against the APK installed on the phone):
`f0f5d60a89e361e84573b419443e74e1b19b9787a8bd23944de237661c8b3d94`.
Evidence: build/legal-audit/mi-a2-software-decoder-5.log,
app-regression-after-legal.log, legal-stabilization-gate.log and
decoder-lint-kotlin-model.log. Generated binaries/logs remain ignored.

Synthetic fixtures contain only original test patterns/sine waves. The standalone
CLI encoder used to generate them is a development tool, **not shipped code**.

## Not yet verified or completed

- Replace app dependency/provider and remove old keep rules after a fail-closed,
  reproducible native build/bootstrap is integrated into local builds and CI.
  An ignored build/native-libraries directory alone is not a distributable build.
- Exact corresponding-source package, source hashes/configs/patches and practical
  APK relinking/resigning instructions must accompany distribution. Never publish
  private signing keys. Published old GPL-wrapper releases need a separate remedy.
- Real armv7/x86_64 runs (all three ABIs compile; current phone run selects arm64).
- HEVC and additional audio codecs are capability-probed, not all decoded in this
  suite. HDR/rotation, adaptive streams, seek/cancellation during in-flight work,
  high-resolution malformed media and long sessions require additional tests.
- 1080p/4K software decode memory/CPU/battery budgets versus NextLib. Tiny 128x96
  fixtures are functional tests, **not performance acceptance**.
- Minified release/Play, full UA-Cast ViewModel/player/Cast/DLNA regression with
  the replacement actually connected, and final APK licence/ABI inspection.
- Public privacy deployment, artwork provenance, EPG permissions, top-level Cast
  entry-point verification and publisher/seller review remain separate blockers.

Signed-release CI intentionally fails while config/legal/release-readiness.json
is unapproved or NextLib remains on the runtime graph. A successful build/test is
not a legal opinion and is not permission to publish a noncompliant release.
