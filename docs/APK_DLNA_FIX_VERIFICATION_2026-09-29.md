# APK selection and DLNA status — verification, 2026-09-29

This pass completes the two findings from the preceding audit. It does not establish that every
line in the repository is bug-free or that the Hisense VIDAA network error has been reproduced.

## Changes

- `ReleaseApkPolicy` no longer chooses a single ABI-labelled APK without checking the device's
  supported ABIs. Exact ABI matching prevents `x86` from selecting `x86_64`. Both hyphen and
  underscore filename separators are recognised. `nonuniversal` is not a universal marker, and
  a universal marker cannot override an explicit incompatible ABI. Existing single generic APKs,
  matching splits, ABI fallback and real universal APKs remain supported.
- An unsupported DLNA transport-status query now publishes `playbackStatusUnverified`, guarded
  by the active connection generation and device. The device sheet shows an amber
  "Playback status unavailable" message instead of claiming verified casting. The message is
  translated into English, Ukrainian, Russian and Spanish; the Stop action remains available.
  Unsupported status is not treated as a disconnect: doing so would stop working legacy renderers.

## Regression evidence

- Three adversarial APK filename tests failed on the first implementation, then passed after
  tightening marker detection. Positive matching/uppercase/underscore cases also pass.
- `DlnaRendererWatchdogTest` verifies that an unsupported query reports the unverified state once,
  without disconnecting the renderer or repeatedly polling it.
- `DlnaConnectionUiTest` checks the unverified label, absence of the confident Casting label and
  continued operation of the Stop button on a 320 dp-wide UI.

## Final verification

Final Gradle gate completed successfully in 4m 17s, offline with two workers:

```powershell
.\gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleRelease '-Puacast.requireSigning=true' --max-workers=2 --offline --no-daemon --console=plain
```

- Debug: 2,254 tests; release: 2,018 tests; zero failures, errors or skipped tests.
- Android lint (debug/release), detekt and `git diff --check` passed.
- Release universal APK passed `apksigner verify --verbose`; v2 and v3 signatures are valid.
- Two R8 toolchain warnings remain: its class-file resource provider does not support asynchronous
  parsing. Neither prevented minification or packaging; no toolchain migration was made here.

## Local artifact and limits

- Artifact: `app/build/outputs/apk/release/app-universal-release.apk`.
- Version remains `0.9.7`, universal versionCode `164`; size: 24,068,900 bytes.
- SHA-256: `707AE7DACEE19D9A1139F5F451F18918AAF3ABDAA6FB56B252E492DD23A0F60B`.
- No Android device was connected in `adb devices`. No hardware/Hisense test or Google Play test
  was performed. The unverified DLNA label is not evidence that "Error de red" has been fixed.
- This is a local testing build, not a new published update. No version bump, GitHub publication,
  installed-app mutation or unrelated worktree cleanup was performed. A new public update still
  requires a higher version and the applicable hardware/CI release checks.

Later follow-up on the same date: `CAST_DIAGNOSTIC_FIX_VERIFICATION_2026-09-29.md` records the
additional Cast fixes, full variant gate and newer local APK hash. The hash above identifies this
earlier APK/DLNA build, not the file currently at the output path.
