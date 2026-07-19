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
