# Releasing / packaging the source

To hand off or archive the source tree, package a clean snapshot from git history rather than
zipping the working directory directly:

```bash
git archive -o ua-cast.zip HEAD
```

This produces exactly what's committed at `HEAD` - nothing else.

## Why not just zip the working directory

The raw working directory routinely contains things that must never leave this machine:

- **`.git/`** - full history, including anything ever committed and later "removed" (still
  recoverable from old commits).
- **`build/`, `app/build/`** - generated output; large, and not portable across machines/SDKs.
- **`.claude/`** - agent working artifacts (worktrees, logs). Logcat captures under here can
  contain full stream URLs, including Xtream credentials passed as query params - see the
  `.gitignore` entry for this directory.
- **`local.properties`, `*.jks`, `*.keystore`** - local SDK paths and, if present, signing keys.

`git archive` sidesteps all of this automatically: it only ever includes tracked files at the
requested commit, so anything git-ignored (or never committed) simply isn't in the output.

## Which APK to ship

`./gradlew :app:assembleRelease` produces four APKs, not one (see the `splits` block in
`app/build.gradle.kts`):

| File | Size | For |
|---|---|---|
| `app-arm64-v8a-release.apk` | ~11.9MB | every current phone |
| `app-armeabi-v7a-release.apk` | ~11.5MB | older 32-bit ARM devices |
| `app-x86_64-release.apk` | ~12.9MB | emulators |
| `app-universal-release.apk` | ~23.1MB | when you cannot ask what CPU the target has |

Native code (FFmpeg, via `nextlib-media3ext`) is ~78% of this app, so a per-ABI APK is less than
half the size of the universal one. Hand out the universal APK for a plain download link; upload
the three per-ABI APKs together if a store accepts multiple APKs per release. 32-bit x86 is not
built at all (see `ndk.abiFilters`).

For Play Store specifically, use `./gradlew :app:bundlePlay` - Play performs the same split
server-side from a single `.aab`, while the `play` build type removes the sideload updater,
`REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, and its install-result receiver.
Never upload `bundleRelease`: that is the sideload distribution and intentionally keeps those
capabilities. `scripts/check-play-distribution.sh` verifies the merged Play manifest and
`BuildConfig` after packaging.

Every APK of a release gets its own `versionCode` - base × 10 plus a per-ABI digit, see
`abiVersionCodeOffsets` in `app/build.gradle.kts`. A store rejects multiple APKs sharing one
`versionCode`, and it has no way to tell which one to serve an upgrading device.

**The universal APK is the highest of the four, not the lowest** (offset 4, above `x86_64`'s 3).
This paragraph used to say it kept the plain base code, which is what the build actually did until
an off-by-one was found: that put universal *below* all three per-ABI APKs, and a `versionCode` that
goes down is not an update - Android refuses the install and tells the user only "App not
installed". Universal is the build that runs everywhere, so it is the one every other install must
be able to move *to*. `scripts/check-version-code-ordering.sh` reads the ordering back out of
`output-metadata.json` after a release build, because a comment cannot be wrong about what was
actually built and a check can only be wrong about nothing.

## Versioning

`versionCode`/`versionName` are supplied at build time via `-Puacast.versionCode` /
`-Puacast.versionName` (see `android-ci.yml` for how CI derives them from the run number) rather
than hardcoded in `build.gradle.kts` - a local `./gradlew :app:assembleRelease` without these
properties falls back to the defaults in `app/build.gradle.kts`.

Marking a new version touches **three** files, and they have to move together:

1. `app/build.gradle.kts` - the `versionCode`/`versionName` defaults a local build falls back to.
2. `.github/workflows/android-ci.yml` - `UACAST_VERSION_NAME`, which carries the same
   `major.minor.patch` with the run number appended. Leaving this behind makes CI artifacts claim
   the previous version, which is worse than no version at all since it looks authoritative.
3. `CHANGELOG.md` - a new section at the top. A version number with no record of what is in it
   tells a user nothing.

`versionCode` only has to increase monotonically; CI derives its own from the run number, so the
default in `build.gradle.kts` matters only for locally built APKs.

### Publishing the release, and why the tag is now load-bearing

The app checks for updates itself: once a day when it enters the foreground (one-hour retry after
a failed check), and on demand from Settings ->
Updates. It asks
`https://api.github.com/repos/Dovbnyak28/UA-Cast/releases/latest` and compares that release's
`tag_name` against its own `versionName` (see `com.uacastplayer.update`). Two consequences for
this runbook:

1. **A version only exists to installed apps once there is a published GitHub Release for it.**
   Pushing a tag is not enough, and neither is a draft or a pre-release - `/releases/latest` skips
   both, and the parser re-checks the flags anyway. Until the release is published, every installed
   copy is correctly told it is up to date.
2. **The tag has to be a version number**, with or without a leading `v`: `v0.10.0`, `0.10.0` and
   `1.0.0-rc1` all parse; `nightly` or `release-2026` do not, and a release tagged that way is
   ignored rather than guessed at. Comparison is numeric per component, so `v0.10.0` is correctly
   newer than `v0.9.0` - and a CI build reporting `0.9.0.147` is newer than the `v0.9.0` release it
   came from, so it is not offered an "update" back to itself.

3. **Attach the APKs to the release, or the install path never engages.** A newly found release
   with an installable APK raises an install invitation showing a bounded plain-text preview
   of the GitHub Release body; write useful, user-facing release notes. The user must choose to download.
   Choosing "Remind me later" retains the banner and offers the dialog again after at least three days.
   The app can now download
   and install an update itself - it did not always, and this paragraph used to say so. It picks an
   attached asset via `ReleaseApkPolicy` (universal wins when present), verifies size and any
   published `sha256`, and refuses anything not signed by whoever signed the running copy
   (`ApkSignatureGate`). With **no assets attached** every one of those steps is skipped and the
   only thing the banner can offer is the release page in a browser - whose most prominent
   downloads are GitHub's own source archives, which are not installable. That is the state a
   release with no APK puts every user in, and it looks like the feature is broken.

Every release APK must be signed with the *same* key as the one it is replacing. Android refuses an
APK signed with a different one, and the only way out for the user is to uninstall - taking their
playlist, their guide and their licence with it. `ApkSignatureGate` refuses such a file before the
system dialog can, which is verified on a real device by
`UpdateInstallChainInstrumentedTest`.

Local diagnostic builds may still use the normal Gradle fallback signing configuration. For a
production release, make the requirement explicit:

```bash
./gradlew :app:bundlePlay -Puacast.requireSigning=true
```

With that flag the build fails before packaging when the `UACAST_*` signing properties are absent.
The ordinary `android-ci.yml` release job is deliberately an **unsigned packaging diagnostic** and
names its artifacts accordingly. It is not a distribution source.

For a signed Play artifact, manually dispatch `.github/workflows/signed-release.yml` with an
explicit monotonically increasing `version_code` and the release `version_name`. Its `production`
environment needs these protected secrets:

- `UACAST_KEYSTORE_BASE64` - base64 of the PKCS#12 keystore;
- `UACAST_STORE_PASSWORD`;
- `UACAST_KEY_ALIAS`;
- `UACAST_KEY_PASSWORD`.

The workflow materializes the key only under the runner's temporary directory, passes
`-Puacast.requireSigning=true`, checks the merged Play permission surface and legal assets, verifies
the AAB and universal APK signatures, then uploads separate signed Play AAB and GitHub APK
artifacts. Attach the **universal APK** artifact to a published GitHub Release with the matching
version tag; an Actions artifact alone is not visible to the app's updater. The workflow does not
publish to Play Console or GitHub Releases; those remain explicit human release steps. Never commit the keystore, passwords, generated
`gradle.properties`, or a base64 copy of the key.

### Which digit moves

Semantic versioning, with the meanings pinned to what a *user of the APK* experiences - this app
publishes no API, so "breaking change" has to mean something they can feel:

| Digit | Moves when | Examples from this project |
|---|---|---|
| **major** | Something a user relies on stops working the way it did, or the app is claimed stable for people other than its author | raising `minSdk` past a device that used to run it; a stored playlist/favorites format that an older build cannot read back; removing a feature |
| **minor** | A new user-visible capability, or a behaviour change worth noticing | DLNA casting; the Midnight theme; the local player's behaviour changing during a remote cast |
| **patch** | Fixes and corrections only, nothing new to learn | the splash mask crop; the `701` refusal on channel switch; contrast repairs |

Refactors, test additions, CI work and doc changes move nothing on their own. They ride along with
whatever release ships next.

The fourth component CI appends is **not** part of this scheme. `0.9.0.147` is "0.9.0, built by run
147" - it exists so two artifacts of the same version are distinguishable, and it resets nothing and
means nothing about content.

### What 1.0.0 waits for

The current version is 0.9.7 and deliberately not 1.0.0. The gap is not a feature list; it is evidence:

1. **The app has run on hardware nobody here chose.** Every device it has been verified on - one
   Xiaomi phone, one Samsung UE40KU6000, one Chromecast 4th gen - belongs to its author. A first
   report from a stranger's TV is worth more than another 100 tests.
2. ~~**A signing key exists and is backed up.**~~ Done (2026-08-08). Release builds are signed from
   a PKCS#12 keystore held outside the repository, with its path, alias and passwords supplied by
   four `UACAST_*` properties in `~/.gradle/gradle.properties` - never in the project. Losing that
   file means the app can never be updated again, so its backup is the release process, not a step
   in it.
3. ~~**The instrumented tests cover more than the launch path.**~~ Done (2026-08-16):
   `OK (49 tests)` on a Mi A2 through `scripts/run-instrumented-tests.sh`, up from nine. The
   sentence this used to end on - "the whole Cast/DLNA/proxy path is still held up by unit tests
   over pure policy objects" - is what changed. On the device now: the proxy over a real socket
   (Range forwarding, CORS preflight, method refusal, session-token expiry, wrapper unwrap,
   rewritten segment URLs, concurrency), both stream-rewriting routes (HLS→TS flattening and
   raw-TS→HLS remux), the DLNA control stack against a fake UPnP renderer (relative control URLs,
   a `701` retried through, a `716` refused at once, volume, an unreachable renderer), the update
   chain including `ApkSignatureGate` - which cannot be tested off a device at all - and the
   player's video-fit setting end to end.

   What is still missing is point 1, and no test replaces it: a report from hardware nobody here
   chose.

Point 1 is not a code change, which is exactly why it does not get closer by writing more code.

## Regenerating the baseline profile

**Current status: successfully run once (2026-07-30), on a Pixel 10 Pro emulator (API 37,
x86_64)** - `app/src/main/baseline-prof.txt` now leads with roughly 25,000 lines of real per-method entries
from that run (1,667 `com.uacastplayer` methods covering the language picker -> Terms -> first-run
walkthrough -> Home flow), followed by the previous hand-authored wildcard block as a safety net.
The generator now continues through a credential-free variant-only fixture into Channels, first
player launch, fullscreen and EPG; regenerate the profile to replace the older Home-only capture.

```bash
./gradlew :app:generateReleaseBaselineProfile
```

Run this only on an emulator or device dedicated to profiling. The generator force-stops the target
app and replaces the fixture-owned playlist/EPG state with synthetic data before entering the
player. The benchmark driver no longer runs a blanket `pm clear` by default, but Gradle may still
uninstall the target package when the run finishes; real app data on that package is not a
benchmark input and is not guaranteed to be preserved.

This requires a **connected device or running emulator** (there's no Gradle-managed emulator
configured in this project - `useConnectedDevices = true` in `baselineprofile/build.gradle.kts`).
It builds a throwaway `nonMinifiedRelease` variant of `:app` (applicationId `com.uacastplayer`, no
`.debug` suffix) plus the `:baselineprofile` instrumentation APK, installs both, and runs
`connectedNonMinifiedReleaseAndroidTest`, which should overwrite `app/src/main/baseline-prof.txt`
with the result on success.

**The Mi A2 cannot do this at all, and it is not flakiness.** Android 11 / API 30, LineageOS,
rooted with Magisk. Run without a rooted adb session it fails in seconds and says why:

    java.lang.IllegalArgumentException: Baseline Profile collection requires API 33+, or a
    rooted device running API 28 or higher and rooted adb session (via `adb root`).

`adb root` does succeed on this ROM - adbd comes back as uid 0. The run then gets further, logs
`ProfileInstaller: Installing profile`, sits for nine minutes, and fails with:

    java.lang.ExceptionInInitializerError
    Caused by: java.lang.IllegalStateException: UiAutomation not connected, UiAutomation@…[id=-1]

which is UiAutomator refusing to attach to an instrumentation whose adbd is running as root. So the
two requirements exclude each other here: without `adb root` the collection refuses to start, with
it the automation driving the app cannot connect. This is what the previous note recorded as "hung
25+ minutes with no crash" - the same dead end, seen before the timeout was waited out.

Two side effects worth knowing: `adb root` also brings adbd up on TCP/IP, so `adb devices` starts
showing the phone twice and every later command needs `-s` or an `adb disconnect`; and `adb unroot`
puts it back.

**So: use an API 33+ device or emulator, where no root is involved at all.** Below API 33 this needs
a device whose adb can be rooted *and* whose UiAutomation survives it, which a Magisk-rooted user
build is not.

**What worked**: a Pixel 10 Pro emulator (`emulator -avd Pixel_10_Pro`, AVD image
`google_apis_playstore_ps16k`/android-37.1) doesn't hang, but hits a *different*, environment-specific
snag - `./gradlew :app:generateReleaseBaselineProfile` still fails, every time, at
`:baselineprofile:connectedNonMinifiedReleaseAndroidTest` with `Failed to receive the UTP test
results`. The on-device test itself is not the problem: its own logcat shows `OK (1 test)` and
`Benchmark: Baseline profile for com.uacastplayer is stable` (stable after 5 iterations) every
time - only Gradle's Unified Test Platform result-collection channel back from the emulator fails
in this sandboxed environment, so the task still reports FAILURE and never copies the profile back.
Gradle's own post-task cleanup then uninstalls both APKs regardless, so there's nothing left on the
device to retrieve after the fact.

**Workaround** (bypasses UTP entirely, reuses the APKs Gradle already built under
`app/build/outputs/apk/nonMinifiedRelease/` and `baselineprofile/build/outputs/apk/nonMinifiedRelease/`
from the failed Gradle run above):

```bash
adb install -r app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb install -r baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
adb shell am instrument -w -e class com.uacastplayer.baselineprofile.BaselineProfileGenerator#generate \
    com.uacastplayer.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb pull "/storage/emulated/0/Android/media/com.uacastplayer.baselineprofile/BaselineProfileGenerator_generate-baseline-prof.txt" .
```

Running the instrumentation directly like this skips Gradle's uninstall-on-completion behavior, so
the output file is still on the device afterward to pull. The scripted flow now covers the language
picker, Terms, guided tour, Home, a synthetic playlist restore, Channels, player, fullscreen and
EPG. The synthetic state is prepared by activities compiled only into `benchmarkRelease` and
`nonMinifiedRelease`; `debug` and shipping `release` do not contain them. Append the previous
wildcard block (`HSPLcom/uacastplayer/**->**(**)**` +
`HSPLandroidx/media3/exoplayer/**->**(**)**`/`common/**`) to the end rather than replacing it
outright, as a safety net for decoder/recovery paths one short invalid-local stream cannot exercise. Verify the merged
file compiles before committing: `./gradlew :app:compileNonMinifiedReleaseArtProfile`.

Once a run succeeds, review the diff before committing - a profile that shrank a lot usually means
the generator's UI automation didn't get as far as it used to (see the next paragraph), not that
the app suddenly needs less warm code.

## Running Macrobenchmarks

Use an emulator or a device dedicated to measurements. Every benchmark owns its precondition and
force-stops `com.uacastplayer` before writing a credential-free fixture. The driver does not clear
the whole package by default, but the Gradle task can still uninstall the target during setup or
cleanup; do not point it at an install whose app-private playlist/EPG data matters. No provider
credentials or external server are involved.

```bash
# deterministic 400-channel cold/warm startup
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uacastplayer.baselineprofile.StartupBenchmark

# 40,000-channel restore/open, first player, fullscreen and EPG guide
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uacastplayer.baselineprofile.CriticalJourneysBenchmark

# production SAX + retention + heap budget + index build over 350,000 XMLTV programmes
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uacastplayer.baselineprofile.EpgParseBenchmark
```

The critical journeys report frame timing and peak memory; the XMLTV benchmark reports peak memory
plus the `UaCastEpgParseAndIndex` trace-section duration. The parser uses the target device's actual
`Runtime.maxMemory()` budget, so a 128MB device measures the same capped path production runs.

**The same UTP failure hits the ordinary instrumented tests**, not only the profile generator:
`./gradlew :app:connectedDebugAndroidTest` reports FAILURE with `Failed to receive the UTP test
results` while the device's own logcat shows the suite passing. So the tests are run the same way
the profile is, bypassing Gradle's result channel:

```bash
scripts/run-instrumented-tests.sh
```

That script is the whole route - build both APKs, install with `-r`, run through `am instrument` -
and it is what CI's `instrumented` job runs too, so a local pass and a CI pass mean the same thing.

It also inspects the runner's output rather than its exit code, because **`am instrument` exits 0
whether the tests passed or failed**: pointed at a class that does not exist it prints
`FAILURES!!!` and still returns 0. A check that trusted the exit code would be green forever.

Last run: `OK (49 tests)` in about 74s on a Mi A2 (Android 11; 73,657 ms in the latest run). Note that `connectedDebugAndroidTest`
**uninstalls the app under test when it finishes**, taking the imported playlist, the EPG snapshot
and the icon cache with it - so on a phone carrying real data, use the script above, which does
not.

**`./gradlew build` does not need a device**, though it used to demand one. The Baseline Profile
plugin attaches profile generation to `:baselineprofile:assemble`, and `build` is `assemble` plus
`check` in every module, so the root `build` reached `connectedNonMinifiedReleaseAndroidTest` and
sat there. `:baselineprofile`'s `build` is now bound to compiling and packaging its two variants
plus `check`; generating a profile stays an explicit request, exactly as described above. A full
`./gradlew build` takes about two minutes on this machine.

**Why the first-run part clicks by accessibility role/tree-order instead of text**: none of the three
gate screens have `testTag`s, and `:baselineprofile` is a black-box `com.android.test` module (no
Compose semantics access across the process/APK boundary), so button labels would render in
whatever language the connected device's system locale resolves to - text matching would make the
script device-dependent. Tree order happens to disambiguate every gate correctly instead (see the
generator's own doc comment) - if a gate screen's layout order ever changes, the generator's click
targets need to move with it. After the fixture is installed it explicitly selects English, so the
player/EPG journey can use stable accessibility text without depending on the device locale.

## Measured performance before release

Before a release, also run **Measured performance gate** and review its complete eight-journey
artifact. The scheduled/manual workflow and initial budgets are documented in `PERFORMANCE.md`.
A compiled benchmark APK or an emulator-free host test is not a measured pass. Do not raise a
budget to hide a regression; investigate the trace and validate on a low-end physical device.

## Lite and one-time Premium

A fresh install starts in Lite. Premium unlocks every implemented feature with one non-expiring
purchase. There are no new trials, subscriptions, add-on packages or recurring plans.

The current checkout flag `PremiumAvailability.STORE_IS_LIVE` remains `false`: the release
provider cannot take payment yet. Settings still explains Lite/Premium, and an unavailable
catalogue displays an explanation rather than an unpriced checkout button. A cached paid licence
continues to work offline; an unavailable store never grants unpaid Premium access.

### Configure the single product

| Id | Type | Access |
| --- | --- | --- |
| `premium_lifetime` | one-time in-app product (`inapp`) | every feature, without expiry |

Keep this existing ID exactly. Activate it and its price in Play Console, publish a compatible
signed build on a testing track, and verify checkout/restore with a licence tester before enabling
`STORE_IS_LIVE`. Prices are read from the store in the user's currency.

`premium_monthly` and `premium_yearly` are recognised only for ownership restoration. They are
excluded from the sale catalogue and checkout rejects attempts to buy them. Do not rename or
delete owned product IDs while migrating; stop offering their new sales in Console. Existing
paid rights retain their original expiry/store ownership. The app does not cancel existing
subscriptions or extend their paid term.

### Verification before enabling payment

- Lite keeps local playback, one playlist, EPG, favourites, themes, Chromecast/remux and PiP.
- One Premium purchase unlocks all capabilities; no additional purchase is required.
- Pending/cancelled/failed attempts do not grant Premium.
- Purchase and restore controls reject concurrent attempts.
- Refund/authoritative ownership removal returns to Lite; a failed query preserves cached access.
- Restore remains reachable with an empty catalogue and reports an actionable outcome.
- Legacy paid signed records retain their source and expiry. Legacy trial/tester records become Lite.
- Real charge, acknowledgement and restore must be verified on a Play track; host/debug tests
  exercise the app's state changes without taking payment.
