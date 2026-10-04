# Secure backup, restore preview, icon-pack check and performance gate — 2026-10-04

## Scope and status

All four requested features are implemented locally. Mi TV functional tests passed; publication
of a separate Android review branch/PR and CI runs is authorized. GitHub execution results must
be reviewed separately; preparing a workflow is not a measured performance pass.
No version bump, release publication, primary-app installation or computer shutdown was performed.
Unrelated working-tree changes were preserved.

## Password-protected portable backup

Premium → Settings → Data → Save to file uses Android's document picker, suggests
`ua-cast-backup-YYYY-MM-DD.uacast`, then requests a matching 12–128-character password phrase.
All-whitespace passwords are refused. Wait for the success message before copying the file to a
computer or another phone; cancelling password entry may leave the newly created document empty.
There is no UA-Cast cloud upload, recovery password, account or device-bound encryption key.

The plaintext remains the existing version-2 portable backup: sources, original local M3U bytes,
favorites and five explicitly selected settings. Old version-1/2 JSON can still be opened and
previewed; new user-facing exports are encrypted. Legacy controller APIs remain available for
existing internal tests, but the production document action supplies a password.

`core/security/BackupCipher.kt` defines this binary envelope:

| Field | Size | Meaning |
| --- | --- | --- |
| Magic | 8 bytes | ASCII `UACASTBK` |
| Envelope version | 1 byte | `1` |
| KDF rounds | 4 bytes | Big-endian `600000`, exact accepted value |
| Salt | 16 bytes | Fresh SecureRandom salt |
| Nonce | 12 bytes | Fresh SecureRandom GCM nonce |
| Payload/tag | variable + 16 bytes | AES-256-GCM ciphertext and authentication tag |

The whole 41-byte header is authenticated as additional data. The KDF is
PBKDF2-HMAC-SHA256/RFC 8018; the JCA-HMAC implementation supports API 24/25 without relying on
their unavailable SHA256 SecretKeyFactory and checks cancellation every 4,096 rounds. Plaintext
is bounded to 8 MiB, envelope to 8 MiB + 57 bytes. Size/version/work-factor checks precede KDF;
no plaintext is used before GCM authentication succeeds. Different exports get different
salt/nonce; passwords, URLs and plaintext are not logged.

CPU work runs on the CPU dispatcher, document I/O off Main. Passwords are not rememberSaveable,
written to preferences or serialized into Activity state. Owned character/key buffers are cleared
after use; password dialogs use SecureOn. This is not a guarantee of perfect JVM memory erasure:
editable strings/JCA internals still exist temporarily in process memory. Losing the password
means losing access to the encrypted file. Encryption does not protect an already compromised
unlocked device or the provider's remote streams.

Design references: [Android cryptography](https://developer.android.com/privacy-and-security/cryptography),
[OWASP password derivation guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[OWASP authenticated encryption guidance](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html).
This implementation is not an independent cryptographic certification.

## Restore preview and ownership

Open document → bounded read → password if encrypted → decode/validate → preview → confirm.
Sources and favorites finish startup loading before preview calculation. The summary lists
admitted sources, new favorites, embedded local playlists, setting names/count and source-limit
skips. It uses the same merge policy and canonical favorite identity as actual import. No source
URL, provider credential or embedded playlist is shown in the summary.

Reading, password entry, validation and preview do not write playlists/preferences/favorites.
Cancel, wrong password or damaged payload leave them untouched. The validated bytes are retained
only in ViewModel memory: confirmation never rereads a potentially changed provider document.
Replaced/cancelled readers cannot publish an old preview. Duplicate confirmation cannot create
duplicate imports. If device state changed and the effective summary changes, confirmation first
refreshes the preview and requires another explicit confirmation. Premium is checked at entry
and again before actual application.

Confirm delegates to the existing single-writer source/favorite/settings controllers and durable
completion reporting. Existing data is merged, not blindly erased. This does **not** add an atomic
transaction across every store or rollback after confirmation: interrupted persistence still uses
the existing failure reporting. Process death discards the pending preview/password; reopen the
file. It remains a selective application backup, not media-file/EPG-cache/PIN/licence/device backup.

## User-triggered logo-pack check

Each explicitly added pack has a Check action. The check samples at most six unique nonblank
`tvg-id` values from the current playlist, reports the total channels lacking an ID, and explains
the expected `base/<encoded-tvg-id>.png` naming. It is a sample, not an exhaustive compatibility
test of every channel. With no matching IDs there are no image requests.

There are at most two concurrent requests, a 10-second total deadline per request and a 256 KiB
limit per sample. A successful result must pass both format detection and real Coil decoding at
a bounded preview size. The dialog distinguishes found image, 404, network failure, invalid image
and oversized response. Preview bytes are reused, not downloaded again. It neither adds a built-in
CDN nor changes sources, cache or preference state; malformed base URLs, credentials/query tokens
and invalid ports are refused. The only requests go to the explicitly selected user pack.

In-flight checking stops with the screen/lifecycle; changing pack/playlist replaces the prior
result. TV text-field up/down navigation and modal D-pad routing are preserved; Cancel remains
reachable. The real Mi TV dialog test exercised decoded image → another pack's 404, verified the
old success label disappears, then closed the dialog with remote Select.

## Regular measured performance gate

`.github/workflows/performance-gate.yml` defines runs for Android changes in pull requests,
manual runs and Monday 04:00 UTC scheduled runs. The schedule becomes active only after merge
to the default branch; a feature-branch run does not activate the weekly schedule. The API-35 x86_64 CI
emulator has 2 GiB RAM / 256 MiB heap. Benchmark errors are suppressed only for `EMULATOR`.

Eight existing journeys must produce complete results: cold/warm startup, restoring/opening
40,000 channels, first player, fullscreen, EPG opening, and parsing/indexing 350,000 programmes.
`config/performance/ci-api35.json` defines initial conservative absolute startup/parse-time,
frame-CPU P95 and worst managed-heap budgets. These are not yet calibrated from a first real CI
run and do not measure all native/GPU memory or guarantee performance on every phone/TV.

The stdlib validator uses actual AndroidX Benchmark 1.4.1 JSON names: sampled `P95`, scalar `runs`,
`memoryHeapSizeMaxKb` and `UaCastEpgParseAndIndexSumMs`. Missing/duplicate/incomplete/zero/non-finite
measurements fail rather than being counted as a pass. Peak memory is not hidden by a median;
duration uses median. Eight validator tests include malformed evidence and all configured metric
names, but their synthetic data is explicitly **not** device measurement evidence. Traces/results
are retained as artifacts for 30 days. See `PERFORMANCE.md` and the release runbook.

Publication: [PR #4](https://github.com/Dovbnyak28/UA-Cast/pull/4) is a draft Android-only review
snapshot, including earlier Android commits absent from master. It does not publish a release or
activate the weekly schedule. Private keys/playlists/recovery archives, unrelated web files and
local physical-device captures are absent from the PR diff.

Required follow-up: complete all eight cases on API 35, review traces and calibrate tighter budgets
from measured evidence. The API-28 Mi TV functional run is not that benchmark run.

### Initial execution found real harness/platform gaps

The first disposable API-36 run finished 4/8: the four UI journeys timed out looking for visible
`Home` text. The current tab is labelled `Overview` and Home's heading is the application name;
the navigation accessibility description remains `Home`. The driver now waits for that existing
description, without changing production UI. The next run finished 7/8: EPG was correctly scrolled
to the current programme, while the driver demanded the midnight `000` slot. Both Macrobenchmark
and profile-generation paths now require a visible, exact-format synthetic programme row instead
of an off-screen historical slot. The corrected EPG journey passed separately (five iterations,
60.024s total). The complete corrected eight-case run then passed in 494.933s with all configured
iterations (10 for each startup, five for each other journey). Its target app predates the
following TV-only Back fix; that pass verifies the harness, not a new signed release. Budgets,
timeouts and assertions about loaded programme data were not removed. Local frame CPU P95 values
were 165.9–477.9ms under Windows software rendering, so this run is explicitly **not** an API-35
budget pass. The first remote API-35 run independently reproduced the midnight-slot selector bug.

Initial remote Android CI passed unit/screenshots, lint/detekt/architecture, unsigned packaging
and API 24. API 36 exposed nine failures in phone-remote Back routing to modal windows: sending
KeyEvents to the decor view did not reach modern Dialog back handling. Installed AndroidX sources
confirm that Compose's ComponentDialog owns an OnBackPressedDispatcher callback respecting
`dismissOnBackPress`. The registry now obtains that owner from the dialog view tree and invokes
it once on an uncancelled key-up, never the obscured Activity. Existing window reference counts,
top-dialog selection and non-Back key routing are unchanged. New host/native tests require that
key-down and cancelled key-up do not dismiss or navigate underneath. The current targeted host
gate passed 213 tests plus screenshot verification, Debug lint, detekt and debug/test APK builds
in five minutes. Current native window/banner/secure-backup-dialog regressions then passed
**16/16 on API 36 (67.123s)** and **16/16 on the actual API-28 Mi TV (55.379s)**. The TV runner
restored and hash-verified all four original debug files/preferences. The local owned-emulator
wrapper initially expected 15 cases instead of the actual 16; its assertion was corrected to
the exact suite count, without changing any test result or allowing a partial pass.

Rebuilt debug APK SHA-256: `6D7A7FEC7EECBF94B2CB313F54A1CA41BC7F7131406AF9657C41F771648450BB`.
Native test APK SHA-256: `BA5503F0BD70C5B356636A9A0F2CC8E98AF9CD6442E46C129A3A7409C5B96852`.

The separate API-30 failure was a banner-test assumption: the system selected the manifest's
declared round phone icon. The native assertion now permits only those two declared phone-icon
resources, while retaining all banner bitmap/dimension/filtering/opacity/memory checks. No
production icon, manifest binding, pixel tolerance or screenshot golden was changed for it.

Superseded PR performance jobs are cancelled; weekly/manual measurement runs keep their normal
ownership. Local API-36 software-rendered frame timings are not used to certify API-35 budgets.

## Verification

The full local gate completed successfully in 9m 53s:

```powershell
./gradlew.bat :app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest `
  :app:lintDebug :app:lintRelease :app:lintPlay :app:detekt :core:test :core:detekt `
  :baselineprofile:assembleBenchmarkRelease --offline --max-workers=1 --no-daemon --continue
```

| Check | Result |
| --- | --- |
| Debug unit | 2,590 tests / 441 suites, no failures/errors/skips |
| Release unit | 2,263 tests / 370 suites, no failures/errors/skips |
| Play unit | 2,263 tests / 370 suites, no failures/errors/skips |
| Core unit | 133 tests / 16 suites, no failures/errors/skips |
| UI goldens | All 47 verified; only the narrow backup hint intentionally re-recorded/visually inspected |
| App/Core Detekt | Passed, no new baseline findings or broad suppressions |
| All three lint variants | No errors/warnings; one existing OldTargetApi hint per variant |
| Repository checks | All 15 top-level shell gates passed |
| Performance evidence validator | 8 tests passed |
| Benchmark harness | Compiled; **not** a measured performance pass |
| Debug/test APKs | Built and installed on isolated Mi TV debug package |

The benchmark-only harness packages unstripped `libbenchmarkNative.so` and `libtracing_perfetto.so`
with the existing toolchain informational notice; this is not a production app crash/lint failure.

Artifacts from the initial 19-case native pass (historical hashes, not a published release;
the later document-picker fix has its own rebuilt artifacts):

- `app/build/outputs/apk/debug/app-universal-debug.apk`, SHA-256
  `519431784DCB3FFDC0D6B395F0E800244B2C00A1D670E74382F75ECF7D1D8EB0`.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, SHA-256
  `E67BB0DE3A61794500819FCBBBAF1312A3F81BFDFEA73F4B6718BD5B3A3A1ED0`.

### Actual Mi TV

Xiaomi MiTV_MSSP2 (`volver`), Android 9/API 28, authorized Wi-Fi ADB, awake screen:

- First run: 16/18. Failures were test assumptions: arbitrary file URI was correctly rejected by
  the existing owned-file policy; raw Compose InputText was incorrectly treated as displayed text.
  Tests were corrected without relaxing security. Displayed EditableText is masked and the
  Password semantic flag remains set.
- Repeated run: **18/18**, including actual JCA AEAD, encrypted UTF-16 local-M3U export/restoration
  after preview, real image decoding/404/corrupt PNG, password/preview D-pad and 12 existing TV
  modal/banner regressions. Duration 189.341s.
- Additional real icon-pack dialog/state-replacement/D-pad test: **1/1**, duration 8.667s.
- Additional native system-picker/guard tests: **2/2**, duration 9.528s. The actual firmware
  handler immediately returns `RESULT_CANCELED` with a null URI instead of showing a picker.
  The application now refuses this exact known OEM stub and displays the existing explanation.
  Ordinary DocumentsUI and similarly named third-party activities remain allowed in host tests.

The isolated review checkout also passed the targeted backup/icon-pack/picker/performance unit
and screenshot gate, Debug lint, App/Core Detekt and Core tests in 3m 45s. This verifies the
transferred Android snapshot; it is not a substitute for the full remote matrix.

The primary `com.uacastplayer` package/data was not installed or cleared. Debug files/preferences
were archived and isolated, then restored. API-28 firmware exposed two runner assumptions:
`run-as pwd` used `/data/data/...`, and `test` was not an executable. The runner now accepts only
the two exact debug-app roots, probes directories with `ls`, refuses ambiguous probes, and verifies
every original file hash against the recovery archive. The first nested folder was repaired
without deleting data; all four original files matched SHA-256, as did both later restorations.
Private recovery archives and fixture logs stay under ignored `app/build/device-audit`; do not
publish them.

The firmware resolves CreateDocument to `com.google.android.tv.frameworkpackagestubs`' DocumentsStub;
its silent cancellation is now confirmed and guarded. The native portable round-trip used owned
app files, not an external USB/system document-picker workflow. Export on this TV still requires
a functional document provider; the guard does not install or implement one. Transferring the
file to another physical device remains unverified. Mi TV API 28
cannot certify the API-35 scheduled benchmark budgets; no GitHub performance run is claimed.
