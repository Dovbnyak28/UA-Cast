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

## Versioning

`versionCode`/`versionName` are supplied at build time via `-Puacast.versionCode` /
`-Puacast.versionName` (see `android-ci.yml` for how CI derives them from the run number) rather
than hardcoded in `build.gradle.kts` - a local `./gradlew :app:assembleRelease` without these
properties falls back to the defaults in `app/build.gradle.kts`.

## Regenerating the baseline profile

**Current status: set up, not yet successfully run.** `app/src/main/baseline-prof.txt` is still
the hand-authored wildcard version (see README's "Stack" section). The `:baselineprofile` module
(`BaselineProfileGenerator` - drives a fresh install through the language picker -> Terms ->
onboarding gate to Home) builds, packages, and installs cleanly:

```bash
./gradlew :app:generateReleaseBaselineProfile
```

This requires a **connected device or running emulator** (there's no Gradle-managed emulator
configured in this project - `useConnectedDevices = true` in `baselineprofile/build.gradle.kts`).
It builds a throwaway `nonMinifiedRelease` variant of `:app` (applicationId `com.uacastplayer`, no
`.debug` suffix) plus the `:baselineprofile` instrumentation APK, installs both, and runs
`connectedNonMinifiedReleaseAndroidTest`, which should overwrite `app/src/main/baseline-prof.txt`
with the result on success.

**What actually happened when this was tried** (Xiaomi Mi A2, Android 11 / API 30, rooted with
Magisk): the whole pipeline up through installing both APKs and starting the instrumentation
worked - logcat shows `TestRunner: started: generate(...)`, then `ProfileInstaller: Installing
profile for com.uacastplayer.baselineprofile` (the test APK's own profile, unrelated to the actual
target), and then **nothing for 25+ minutes**: no crash, no ANR, no system dialog on screen, the
target app (`com.uacastplayer`) never once observed running via `adb shell ps`. Force-stopping the
stuck instrumentation process didn't unblock the Gradle task either; it had to be killed via
`./gradlew --stop`. This matches a known category of `BaselineProfileRule` flakiness on
non-stock-AOSP/modified-ROM devices - it most likely hung inside the library's own ART
profile-reset/app-relaunch handling between iterations, not in `BaselineProfileGenerator`'s own
code (which never got observably far enough to run - the target activity never appeared).

**Next time**: try a stock/AOSP device or emulator (API 33+ ideally, which uses a faster, more
reliable profile-collection path than this API 30 device did) before assuming the module itself is
broken - the build/install side is verified working. If it hangs again on a different device,
capture `adb logcat -d --pid=<baselineprofile test pid>` and compare against the point it stalled
here (right after "Installing profile...").

Once a run succeeds, review the diff before committing - a profile that shrank a lot usually means
the generator's UI automation didn't get as far as it used to (see the next paragraph), not that
the app suddenly needs less warm code.

**Why the generator clicks by accessibility role/tree-order instead of text**: none of the three
gate screens have `testTag`s, and `:baselineprofile` is a black-box `com.android.test` module (no
Compose semantics access across the process/APK boundary), so button labels would render in
whatever language the connected device's system locale resolves to - text matching would make the
script device-dependent. Tree order happens to disambiguate every gate correctly instead (see the
generator's own doc comment) - if a gate screen's layout order ever changes, the generator's click
targets need to move with it.
