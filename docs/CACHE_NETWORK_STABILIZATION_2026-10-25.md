# Cache and network stabilization — 2026-10-25

## Scope and evidence

This pass traces icon resolution from settings/row requests through prefetch, memory cache,
failure records and HTTP, plus ownership of the legacy EPG snapshot stream during coroutine
cancellation. It does not establish that every line of the application is defect-free.
Existing unrelated working-tree changes are preserved.

Four confirmed defects were reproduced before their fixes. The authoritative negative run is
`build/legal-audit/cache-stabilization-red-2026-10-25.log`: **36 tests, 9 failures**.
The nine failures are scenarios of four defects, not nine independently counted bugs.

| Defect | Execution path / consequence | Fix |
| --- | --- | --- |
| Negative icon memory entries never expire | `IconController.resolveChannelIcon` → `IconRepository.resolveIconFile` returns a cached null before consulting `IconFailureStore`. Even after the transient/permanent failure record expires, a visible channel can keep showing no logo until invalidation, eviction or process restart. | Timestamp memory misses with the existing transient failure policy. Both the initial lookup and the lookup after waiting for a resolution lane expire misses. Permanent HTTP failure records still enforce their longer backoff; positive hits remain cached. |
| Changing Wi-Fi-only preference leaves the previous job unchanged | `setIconWifiOnly` updated the preference/UI but the active prefetch retained its old Boolean. Enabling it during a mobile download did not stop that job; disabling it after a refused pass did not resume the remembered request. | On an actual preference change, replace the remembered prefetch through the existing generation/cancellation mechanism. Same-value calls do not interrupt work. Existing playback, device-tier, cache-clear and disposal gates remain in effect. |
| Network eligibility is checked only before the queue starts | `IconPrefetcher.prefetch` could continue dequeuing new resolutions after Internet loss or a Wi-Fi-to-metered transition and report the pass as completed. | Recheck before each queued resolution. An ineligible network terminates sibling workers and the producer through structured concurrency, returning incomplete. A producer cannot remain suspended on a full queue after its workers stop. Allowed metered handover still completes normally. |
| Legacy EPG stream is lost at the IO-to-caller cancellation boundary | `EpgRepository.restoreSnapshot` → `EpgSnapshotStore.open` decodes a v1 `Document` on IO. Cancellation before caller dispatch can discard the result before the caller reaches its `use` block, leaving the stream unclosed. | Keep ownership at the dispatcher boundary until successful delivery; close an unclaimed legacy document and rethrow cancellation. A close failure is suppressed onto the cancellation. Normal delivery still transfers ownership to the caller. |

These are localized reliability/resource defects (P2 in the existing release-risk scale).
They are not evidence of a new universal crash or a newly confirmed Hisense defect.

## Regression coverage

13 new test cases:

- `IconFetchFailureTest`: transient expiry, permanent backoff/expiry, clock rollback.
- `IconPrefetchInterruptionTest`: enable Wi-Fi-only during a held HTTP request, resume a
  genuinely refused pass, and same-value preference idempotence.
- `IconPrefetcherProgressTest`: Internet loss, forbidden metered handover, allowed metered
  handover. The queue is larger than its bounded buffer; interruption tests have a timeout
  so a producer deadlock fails rather than hanging the suite.
- `EpgSnapshotHandoffTest`: cancellation before delivery, close failure during cleanup,
  successful ownership transfer, cancellation before IO starts. Dispatchers are explicitly
  drained to exercise the handoff without timing guesses.

The initial resume test exposed a test race: an immediate policy change could occur before
the first queued selection actually ran. It now drains the single selection worker before
changing the preference. With that correction the unfixed implementation fails as expected.

Focused verification passed all **36 tests**. The first detekt run reported two new return-count
issues; those were corrected without adding suppressions or changing the baseline.

## Verification status

Full regression/build: **BUILD SUCCESSFUL**, 13m 53s, recorded in
`build/legal-audit/cache-stabilization-final-gate-2026-10-25.log`.

| Check | Result |
| --- | --- |
| `:core:test` | 133 tests, 0 failures/errors/skips |
| `:app:verifyRoborazziDebug` | 2,786 tests, 0 failures/errors/skips; all 58 screenshot references verified without re-recording |
| `:app:testReleaseUnitTest` | 2,351 tests, 0 failures/errors/skips |
| `:core:detekt` / `:app:detekt` | Passed; baseline and suppressions not expanded |
| `:app:lintDebug` | 0 errors, 0 warnings; 27 informational SDK/dependency update hints |
| `:ffmpegdecoder:verifySoftwareDecoderNative` | Passed; original native inputs/hashes preserved |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | Passed; instrumentation APK compiled, not run on hardware |

Total: **5,270 tests**, including the 13 new cases. The focused 36 are a subset of this
total, not additional unique cases.

After the NDK packaging correction below, the same gate passed again in 2m 26s:
`build/legal-audit/cache-stabilization-packaging-gate-2026-10-25.log`.
Unchanged unit tests were up-to-date in this second run; packaging and affected lint work reran.

Independent checks passed: architecture boundaries, locale-independent formatting,
`onCleared` cleanup, sensitive-variable logging, Premium purity/gating, Cast icon placement,
benchmark fixture isolation, empty detekt baseline and targeted `git diff --check`.

## Additional measured packaging optimization

The first successful build reported `Unable to strip` for decoder libraries. ELF inspection
rejected treating all of those messages as harmless: the four FFmpeg libraries were already
stripped, but `libffmpegJNI.so` still contained debug sections and a full symbol table for
each ABI. The NDK already existed in `.tools/native-legal`, but was not registered in the
Android SDK and Gradle did not use the decoder's locked NDK revision.

- Root `build.gradle.kts` now configures Android application/library modules from the
  existing `ffmpegdecoder/native-sources.json` NDK version. No duplicated version constant,
  codec change or weakening of the native hash/provenance guard.
- This workstation's SDK now has a versioned junction to the existing NDK 26.1.10909125,
  avoiding another toolchain download/copy. Other build machines need that locked NDK
  installed in their Android SDK.
- Debug and release strip tasks pass without the previous warning. The original native
  libraries are untouched; only packaged outputs have debug sections removed.
- All loader-visible ELF sections (names, type, flags, address, size and content hashes) in
  all 15 libraries match the original binaries, both in the actual debug APK and release
  strip outputs. No `.debug_*` or `.symtab` remains in those outputs. Evidence:
  `build/legal-audit/native-packaging-verified-2026-10-25.json` and its adjacent verifier.
- A fresh universal debug APK is **37,892,100 bytes**, versus **42,938,372 bytes** before:
  **5,046,272 bytes / 11.75% smaller**. The first incremental ZIP retained empty space from
  the larger entries; deleting only the generated universal APK and rebuilding removed it.
- `zipalign -c -P 16 4` and `apksigner verify --verbose` pass. This remains a debug-signed
  `com.uacastplayer.debug` APK, version 0.9.7 / universal code 164, not a public release.

Additional logs: `build/legal-audit/native-release-packaging-2026-10-25.log` (11s, successful),
`build/legal-audit/native-fresh-apk-2026-10-25.log` (14s, successful).

Artifact: `app/build/outputs/apk/debug/app-universal-debug.apk`.
SHA-256: `F342505F24D1C40BF6B79ACCE93549BBB7ECBF030F76FA3957DF3B1707C0C99D`.

## Build environment and limits

- After moving the project to `D:/Projects/UA CAST`, required Gradle artifacts were absent
  from the cache. Java's configured separate trust store prevented retrieval from the
  official repositories. This invocation uses Windows' existing system trust store for
  HTTPS; TLS validation remains enabled. Dependency versions and project repository
  configuration were not changed. The pre-existing local annotation mirror init script
  remains a local build workaround, not a new production dependency.
- ADB currently lists no devices. Local JVM/Compose tests are not a real phone/TV test.
- Network checks stop queued work when a worker next evaluates eligibility. This is not
  a guarantee that a transfer already in flight stops at the exact instant of handover.
- No new background scope, timer, polling loop, UI asset or animation was added. The
  bounded worker count, queue, selected-channel caps and positive icon cache are retained.
  Memory-miss metadata remains bounded by the existing 256-entry LRU.
- This pass does not publish a release, change the app version, or resolve the separate
  Hisense VIDAA, native-decoder runtime acceptance and legal/distribution readiness gates.
