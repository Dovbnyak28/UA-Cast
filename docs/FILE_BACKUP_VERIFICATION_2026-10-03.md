# Portable file backup — 2026-10-03

Historical verification of the original plaintext JSON implementation. The current production
document workflow now exports password-encrypted `.uacast` files and previews every restore;
legacy JSON remains readable. See [2026-10-04 implementation and Mi TV verification](SECURE_BACKUP_ICON_CHECK_PERFORMANCE_2026-10-04.md)
for the updated behavior, security limits and pending performance CI run.

## User workflow

In Premium, open **Налаштування → Дані та сховище → Дані**:

1. Choose **Зберегти у файл**, read the privacy warning and select a destination in Android's
   document picker. The suggested name is `ua-cast-backup-YYYY-MM-DD.json`.
2. Wait for the successful-export message. Copy this one JSON file to a computer or another
   phone using the normal file-transfer tools. No UA-Cast cloud account or server is involved.
3. On the receiving installation, choose **Відновити з файлу** and select the JSON file.

The action remains a Premium feature; Lite cannot bypass the feature gate. A device without a
document-picker application receives a visible explanation rather than a silent failed action.
This does not enable real billing: the existing store-availability switch is unchanged.

## What the file contains

- Saved URL/Xtream source locations and source metadata.
- **Actual original bytes of every saved local M3U playlist**, not just a document URI. UTF-8,
  UTF-16 and Windows-1251 bytes are preserved. Restoration writes these files into the receiving
  application's private persistent storage, independent of the original provider or file.
- Favorite channels, including their playback URLs and relevant channel/header metadata.
- Explicitly chosen icon mode, list density, buffer size and EPG source/custom URL. Device-tier
  defaults are not transferred as if they were user decisions.

It is not a full device backup. Video files, downloaded EPG/icon caches, parental-control PIN,
keystore material, Premium purchase/licence and other settings are not included. A local playlist
containing device-specific media-file paths does not make those referenced media files portable.
Network sources and channels still require access to their provider.

## Limits and privacy

The complete JSON has an **8 MiB limit**, including metadata and the Base64 expansion of local
playlist bytes. All saved local files must remain readable when exporting. If one is missing,
inaccessible or the complete result is too large, export fails; it never silently omits that file.
Do not rely on an output document after a failed or interrupted write.

The file is **not encrypted**. Base64 is an encoding, not encryption. Source URLs and playlist
contents can include provider credentials and private tokens. Keep the file private, like a
password; do not attach it to public issues or publish it. The SHA-256 payload digest detects
accidental corruption; it is not a signature authenticating the sender.

## Format and restoration behavior

- New exports use format version **2**. Version 1 remains readable. Old version-1 backups did
  not embed local playlist bytes, so their FILE sources still depend on the old files/grants;
  they cannot retroactively become portable. Older app builds do not understand version 2.
- Restoration merges sources and favorites with current data rather than clearing the device.
  Matching sources are upserted, favorites are deduplicated, and existing sources are not evicted
  to exceed the limit of **10 saved sources**. Rejected excess sources are reported.
- Selected imported settings are applied through the same setters used by Settings. Unspecified
  settings retain their current/device-default values.
- Version-2 structure, local payloads, canonical Base64 and digests are checked before publishing
  source/favorite/settings changes. Invalid files produce a visible rejection. Imported names,
  IDs and filesystem paths never become writable destination paths.
- Local M3U writes use `AtomicFile`; repeated restoration of the same backup selects the same
  app-owned files. Local files are materialized only for sources admitted by the merge.
- A favorite added, removed or reordered while source persistence suspends is merged from the
  latest live state rather than overwritten by the old snapshot.
- Source/favorite persistence failures are reported, not presented as durable success. This is
  **not a transaction across every settings/store file** and does not promise rollback of a
  partially persisted restore. Keep the backup and retry after resolving storage failure.

Restored M3Us are persistent files, not caches. Automatic reclamation of unreferenced restored
files after source removal or an interrupted import is not implemented in this pass. Identical
repeated imports do not create new copies, but different retired backups can leave files until
app data is cleared. A future cleanup must coordinate with imports and source ownership before
deleting anything; an eager source-removal hook would introduce a removal/reimport race.

## Implementation and regressions

The bounded Android IO adapter is
`app/src/main/kotlin/com/uacastplayer/data/backup/BackupPlaylistFiles.kt`. The existing
`BackupController` owns export/import validation, generations and merge callbacks; the codec
owns versioned JSON, and the existing ViewModel owns application state/settings setters.
Blocking document and file work runs on the IO dispatcher. Cancellation closes active provider
streams and is not swallowed as malformed input.

The existing output-document write now requests `wt`: a shorter replacement must truncate an
older JSON tail. A native regression first writes a deliberately longer previous document.

New host regressions cover missing originals, byte-preserving restoration, corrupt digest,
noncanonical Base64, path/name injection, aggregate size limits, cancellation, full source limit,
storage failure before state publication, concurrent favorite addition/removal and old-format
compatibility. Compose checks exercise Premium/Lite gates, failure feedback and narrow Ukrainian
layout. Existing font-scale checks now exercise the new vertical actions, including scrolling and
click accessibility at 200% text size; no failing case was ignored or added to a baseline.

## Physical-device result

Mi A2, Android 11, isolated debug application:

```text
PlayerLifecycleInstrumentedTest: 15
PlayerRestorationRegressionInstrumentedTest: 2
BackupFileInstrumentedTest: 3
LitePremiumInstrumentedTest: 5
OK (25 tests), 88.623 seconds
```

The backup tests used real Android document streams with synthetic public URLs, not private
provider credentials. They deleted the original UTF-16 M3U, restored from the JSON, recreated
source/favorite repositories, loaded the recovered playlist through the normal loader/parser,
and checked repeated restoration and corrupt-file rejection.

The preservation runner restored original debug data. The primary installed application and its
data were not replaced or cleared. The successful run log is kept locally at
`app/build/device-audit/instrumented-ae323d8d2b1c47eb9b75bfa81659c845.txt`.
Private preservation archives are ignored build artifacts and must not be published.

Earlier runs interrupted by screen locking or a misspelled test-class selector are not counted
as passing evidence. Real third-party cloud providers, every Android version and process death
between different persistence stores are not certified by this run.

## Build validation

The complete gate finished **BUILD SUCCESSFUL in 9m 6s**, with 186 tasks (50 executed,
3 from cache, 133 up-to-date), offline and one Gradle worker:

```text
:core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:detekt
:app:assembleRelease :app:bundlePlay
-Puacast.requireSigning=true --continue --offline --max-workers=1 --no-daemon
```

| Check | Result |
| --- | --- |
| Debug unit/UI + screenshot verification | 2,433 tests, 0 failures/errors/skips |
| Release unit | 2,154 tests, 0 failures/errors/skips |
| Play unit | 2,154 tests, 0 failures/errors/skips |
| Core | 126 tests, 0 failures/errors/skips; unchanged successful result reused |
| App/core detekt | passed, empty reports; no baseline edits |
| Debug/release/Play lint | 0 errors, 0 warnings; existing `OldTargetApi` hint only |
| Signed Release APK and Play AAB | passed |

Variant totals overlap; they are not counts of distinct test cases. The new host coverage adds
22 cases to Debug and 19 to each release variant (the three Compose cases are Debug-only).
The latest separate scoped gate passed debug/test APK assembly, backup and font-scale tests,
debug lint and detekt in 2m 18s. All 48 font-scale cases have zero failures/errors/skips.

The new narrow backup screenshot was deliberately recorded and visually checked, then passed
the full screenshot verification. Unrelated goldens were not rerecorded. Six pre-existing
font-scale checks initially selected the old action labels; their selectors and layout contract
were updated for the accessible vertical actions. Lint also caught two new URI-KTX usages and
an instrumentation SDK annotation; these were corrected and the complete gate rerun.

The existing R8 `ClassFileResourceProvider` asynchronous-parsing diagnostics remain toolchain
warnings, not a newly swallowed lint/detekt finding. No suppression or baseline change hides them.

All **15** repository `check-*.sh` checks passed after the implementation, including final
universal/ABI version ordering. Legal documents were additionally checked in all four release
APKs and the Play AAB. Requesting an AAB in the same Gradle invocation intentionally disables
ABI splits; the separate signed `assembleRelease` finished **BUILD SUCCESSFUL in 1m 4s**.

## Local artifacts

The universal APK is `app/build/outputs/apk/release/app-universal-release.apk`, still
**0.9.7 / 164**, package `com.uacastplayer`, min SDK 24, target SDK 36, with arm64-v8a,
armeabi-v7a and x86_64 native libraries. Its SHA-256 is:

```text
04CA42DA2CFC4589CBFE35B8001F844C5480DE5CE77331603447E8A7345C14E6
```

All four split/universal APKs pass `apksigner verify` with v2/v3 signatures and the expected
certificate SHA-256:

```text
c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e
```

Version codes are armeabi-v7a 161, arm64-v8a 162, x86_64 163, universal 164; the universal
artifact outranks every ABI-specific artifact.

The signed Play AAB is `app/build/outputs/bundle/play/app-play.aab`, SHA-256:

```text
12265795393ECAE048F5D864590CFB2D1119516D180087DC01600C541393A49F
```

JDK verification returned `jar verified` and the same signer fingerprint. It also reports the
self-signed/untrusted-chain certificate, absence of a timestamp and ZIP/JarInputStream layout
diagnostics. These are recorded, not suppressed. No Play Console upload/acceptance or real
billing verification is claimed. The APK is a local verification artifact, not a published update.

No version increment, GitHub publication, main-phone APK replacement or computer shutdown is
part of this backup implementation.
