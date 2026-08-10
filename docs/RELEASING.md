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

For Play Store specifically, prefer `./gradlew :app:bundleRelease` - Play performs the same split
server-side from a single `.aab` and none of the above needs thinking about.

Each per-ABI APK gets its own `versionCode` (base × 10 + an ABI digit, see `androidComponents` in
`app/build.gradle.kts`); the universal APK keeps the plain base code. A store rejects multiple APKs
sharing one `versionCode`.

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

The app checks for updates itself: once a week when it is opened, and on demand from Settings ->
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

The check never downloads or installs anything: the banner and the Settings row open the release
page in a browser. Every release APK must therefore be signed with the *same* key as the one it is
replacing - Android refuses an APK signed with a different one, and the user sees an install
failure rather than an update.

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

The version is 0.9.0 and deliberately not 1.0.0. The gap is not a feature list; it is evidence:

1. **The app has run on hardware nobody here chose.** Every device it has been verified on - one
   Xiaomi phone, one Samsung UE40KU6000, one Chromecast 4th gen - belongs to its author. A first
   report from a stranger's TV is worth more than another 100 tests.
2. ~~**A signing key exists and is backed up.**~~ Done (2026-08-08). Release builds are signed from
   a PKCS#12 keystore held outside the repository, with its path, alias and passwords supplied by
   four `UACAST_*` properties in `~/.gradle/gradle.properties` - never in the project. Losing that
   file means the app can never be updated again, so its backup is the release process, not a step
   in it.
3. **The instrumented tests cover more than the launch path.** They now genuinely run - on an
   emulator in CI's `instrumented` job, and by hand through `scripts/run-instrumented-tests.sh`
   (last: `OK (6 tests)` on a Mi A2). But six tests over app launch, the player's lifecycle and the
   empty-playlist state is not coverage of this app: the whole Cast/DLNA/proxy path is still held
   up by unit tests over pure policy objects plus one person trying it on one phone.

None of the three is a code change, which is exactly why none of them gets closer by writing more
code.

## Regenerating the baseline profile

**Current status: successfully run once (2026-07-30), on a Pixel 10 Pro emulator (API 37,
x86_64)** - `app/src/main/baseline-prof.txt` now leads with 24,973 lines of real per-method entries
from that run (1,667 `com.uacastplayer` methods covering the language picker -> Terms -> first-run
walkthrough -> Home flow), followed by the previous hand-authored wildcard block as a safety net for what that
scripted flow doesn't reach (see below - mainly playback/media3).

```bash
./gradlew :app:generateReleaseBaselineProfile
```

This requires a **connected device or running emulator** (there's no Gradle-managed emulator
configured in this project - `useConnectedDevices = true` in `baselineprofile/build.gradle.kts`).
It builds a throwaway `nonMinifiedRelease` variant of `:app` (applicationId `com.uacastplayer`, no
`.debug` suffix) plus the `:baselineprofile` instrumentation APK, installs both, and runs
`connectedNonMinifiedReleaseAndroidTest`, which should overwrite `app/src/main/baseline-prof.txt`
with the result on success.

**Earlier attempt** (Xiaomi Mi A2, Android 11 / API 30, rooted with Magisk): hung 25+ minutes with
no crash/ANR/dialog after `ProfileInstaller: Installing profile for
com.uacastplayer.baselineprofile` - a known category of `BaselineProfileRule` flakiness on
non-stock-AOSP/modified-ROM devices. Killed via `./gradlew --stop`; never produced a profile.

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
the output file is still on the device afterward to pull. The pulled file only covers what the
generator's scripted UI flow actually reached (language picker/Terms/guided tour/Home) - it contains
almost no `androidx/media3` or `com.uacastplayer.player` entries, since the generator never opens a
channel. Append the previous wildcard block (`HSPLcom/uacastplayer/**->**(**)**` +
`HSPLandroidx/media3/exoplayer/**->**(**)**`/`common/**`) to the end rather than replacing it
outright, so playback still gets AOT coverage the scripted flow doesn't exercise. Verify the merged
file compiles before committing: `./gradlew :app:compileNonMinifiedReleaseArtProfile`.

Once a run succeeds, review the diff before committing - a profile that shrank a lot usually means
the generator's UI automation didn't get as far as it used to (see the next paragraph), not that
the app suddenly needs less warm code.

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

Last run: `OK (6 tests)` in 32s on a Mi A2 (Android 11). Note that `connectedDebugAndroidTest`
**uninstalls the app under test when it finishes**, taking the imported playlist, the EPG snapshot
and the icon cache with it - so on a phone carrying real data, use the script above, which does
not.

**`./gradlew build` does not need a device**, though it used to demand one. The Baseline Profile
plugin attaches profile generation to `:baselineprofile:assemble`, and `build` is `assemble` plus
`check` in every module, so the root `build` reached `connectedNonMinifiedReleaseAndroidTest` and
sat there. `:baselineprofile`'s `build` is now bound to compiling and packaging its two variants
plus `check`; generating a profile stays an explicit request, exactly as described above. A full
`./gradlew build` takes about two minutes on this machine.

**Why the generator clicks by accessibility role/tree-order instead of text**: none of the three
gate screens have `testTag`s, and `:baselineprofile` is a black-box `com.android.test` module (no
Compose semantics access across the process/APK boundary), so button labels would render in
whatever language the connected device's system locale resolves to - text matching would make the
script device-dependent. Tree order happens to disambiguate every gate correctly instead (see the
generator's own doc comment) - if a gate screen's layout order ever changes, the generator's click
targets need to move with it.

## Turning premium on

The premium layer is complete in code and off by one constant. `PremiumAvailability.STORE_IS_LIVE`
is `false`, so `AppViewModel` builds `FakeBillingProvider` instead of `PlayBillingProvider` and
`FeatureManager` refuses to lock anything at all. Flipping it is one line, but the order matters,
and doing it first is the way to ship an app that has taken features away and cannot sell them
back.

### 1. Create the products in Play Console, spelled exactly

`com.uacastplayer.premium.billing.PremiumProducts` is the whole catalogue, and Play has no concept
of a typo here: an id the console does not know is simply **absent from an otherwise successful
response**. Nothing is logged, nothing fails, the price never appears and the buy button does
nothing.

| Id | Where in Console | Type |
| --- | --- | --- |
| `premium_monthly` | Monetise → Subscriptions | subscription, monthly base plan |
| `premium_yearly` | Monetise → Subscriptions | subscription, yearly base plan |
| `premium_lifetime` | Monetise → In-app products | one-time purchase |

`premium_lifetime` must **not** be created as a subscription. Subscriptions and one-time purchases
are separate catalogues in Play, queried separately and owned separately, so it would be asked for
in the wrong one and never found. `PremiumProductsTest` holds all of this still from the app's
side; only the console can confirm the other half.

Each subscription needs an active base plan with a price in at least one country, and each product
needs to be **activated** - a draft product is not returned to the app.

### 2. Get a build onto a track

Products are not queryable until a build containing the `com.android.vending.BILLING` permission
has been published on some track (internal testing is enough) and processed. Testing purchases
without being charged also requires the accounts to be added under **Setup → License testing**;
licence testers see "(test)" prices and are not billed.

### 3. Only then flip the constant

```kotlin
// app/src/main/kotlin/com/uacastplayer/premium/PremiumAvailability.kt
const val STORE_IS_LIVE = true
```

It is a `const val` deliberately: R8 folds the branch, so a build with it off carries no billing
code path and a build with it on carries no fake. Verified by unzipping
`app/build/outputs/apk/release/app-universal-release.apk` and reading `classes.dex` - with the flag
off, `premium_monthly` is not in the APK at all; with it on, the product ids, the billing client
and `ProxyBillingActivity` all survive minification, and `com.android.vending.BILLING` is in the
merged release manifest.

`FeatureManagerTest.aBuildWithNothingToSellUnlocksEverything` asserts the flag is still `false`, so
flipping it turns that test red. That is the reminder to read it, not a failure - it describes the
pre-store build and must be updated in the same commit.

### What happens if step 1 or 2 was missed anyway

Nothing locks. `PremiumRepository` asks the store for its catalogue once it connects - on its own,
without waiting for anyone to open the premium screen - and if the answer is empty, `storeCanSell`
stays false and `FeatureManager` keeps every gate open. The rule is `mayWithhold`: *only a build
that can also grant is allowed to withhold*, and a store with an empty catalogue cannot grant.

The flag latches once a real price has been seen, and is persisted
(`LicenseStorage.storeHasEverOfferedProducts`). It has to be, or the first offline launch would
hand the app out for free.
