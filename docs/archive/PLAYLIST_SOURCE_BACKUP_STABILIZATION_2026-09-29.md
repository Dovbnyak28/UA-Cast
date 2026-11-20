# Playlist source and backup stabilization — 2026-09-29

Scope: startup source-list loading, adding/cancelling playlists, legacy snapshot migration, and backup import/export. Existing Cast/DLNA work and unrelated dirty workspace files were not changed in this pass.

## Confirmed failures and fixes

1. **Saved sources overwritten during startup.** A new playlist load cancelled the startup job while its source-list read was suspended. The add then persisted a list containing only the new source. A real-store test failed before the fix with one source on disk instead of two. Source initialization now has a ViewModel-owned, shared await boundary independent of cancellable channel loads. Capacity is rechecked against the loaded list before network access.
2. **Backup import raced the startup read and its own write.** The merge could use the initial empty list; the success result could arrive before the asynchronous source replacement was applied. Import now waits for source and favorite initialization, joins the source write, and rejects an older import's settings callback after a newer import starts.
3. **Cancelled add left phantom channels.** Back after channel loading but before source acceptance left the unsaved playlist on screen under the previous active source ID. The controller now restores the prior UI state (or reloads the saved source), removes the unsaved snapshot, and defers cross-controller loaded effects until the user accepts the source.
4. **A failed legacy migration could be hidden by a new source.** A failed copy left the old snapshot as the only copy, but saving a new source could make the next startup skip legacy migration. Adds and backup mutations now refuse to replace source state while initialization fails; early export reports failure without opening the output file. Once storage is repaired, the same session can retry migration and then save both sources.

## Verification

- Red-before-fix tests covered the startup overwrite, capacity bypass, phantom channels, and failed legacy-copy path. Controls cover successful acceptance, cancellation before network, rapid replacement, recovery after repair, and backup result ordering.
- `:core:test` 120/120; `:app:testDebugUnitTest` 2304/2304; `:app:testReleaseUnitTest` 2068/2068; `:app:testPlayUnitTest` 2068/2068; no failures, errors, or skips.
- `:core:detekt`, `:app:detekt`, `:app:lintDebug`, `:app:lintRelease`, architecture boundary script, and `git diff --check` passed.
- `:app:verifyRoborazziDebug` passed against the checked-in screenshot goldens; no golden files were changed.
- Signed `:app:assembleRelease` passed. The universal APK is `app/build/outputs/apk/release/app-universal-release.apk`, version 0.9.7, code 164, 24,068,900 bytes, SHA-256 `FC7B98EA410275E3627089395BC9D7AAA9CEA628047DC617C32AB1C4348B9687`; `apksigner verify` confirmed one signer and v2/v3 signatures.
- R8 emitted only the same two class-file resource-provider async-parsing warnings seen before this pass.

## Limits and release status

No Android device was visible to `adb devices -l`; on-device migration, install/update, Cast, and Hisense VIDAA DLNA playback were not revalidated. These fixes do not establish that the TV's earlier “Error de red” is resolved. No GitHub publication, version-code increase, or installation occurred in this pass. An APK with the unchanged version code 164 is **not** a newer update for a phone already running code 164.
