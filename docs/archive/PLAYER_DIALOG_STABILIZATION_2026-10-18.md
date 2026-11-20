# Player dialog stabilization — 2026-10-18

## Scope

Follow-up to `UI_DLNA_STABILIZATION_2026-10-18.md`. This pass checks player dialogs,
large system fonts, repeated modal opening and phone-to-TV input. Existing unrelated
worktree changes are preserved. No release, dependency, decoder or playback-state changes.

## Confirmed and fixed

### P2 — Sleep timer actions disappear in landscape with large text

- Source: `app/src/main/kotlin/com/uacastplayer/ui/components/SleepTimerDialog.kt`.
- Trigger: 640 × 360 dp landscape, Android system font scale 2.0, active timer.
- Before: the non-scrolling `Column` runs out of height. The 60-minute option has
  clipped text; the turn-off action is inaccessible. Reproduced for en/uk/ru/es.
- Fix: keep the title and Close button outside a vertically scrollable options list.
  Give each option a minimum 48 dp layout height, preventing cramped touch targets.
- Timer ownership, expiry, cancellation and callbacks are unchanged.

### P2 — Close cannot receive TV focus after reopening the timer

- Same source, `confirmButton`.
- Reproduction: open the timer, dismiss via the production phone/TV Back dispatcher,
  reopen, then request focus on Close. Before the fix, the node remains unfocused.
- Fix: apply the existing `tvFocus()` convention to Close. This supplies the TV
  focus policy and visible focus outline without adding another focus target.
- Regression checks exercise Back, Close, timer cancellation, callback counts and
  dialog registration release, without dispatching to the underlying screen.

## Findings rejected / test-harness corrections

- The initial `hasVisualOverflow` assertion falsely reported horizontal clipping:
  the cached paragraph occupied the available width while the Text node used its
  shorter measured width. Rendered glyphs were not clipped. Those early failures
  are not counted as application bugs.
- A `LocalDensity` override around an Android dialog did not set its window's
  actual font scale. Tests now use `RuntimeEnvironment.setFontScale(2f)`, assert
  the text layout's actual density, and restore the system scale afterwards.
- With the corrected tests, stream information fits both 320 × 480 dp portrait
  and 640 × 360 dp landscape in all four languages. Its layout is not changed.
  `QualityDetailsDialog` is extracted from the existing caller solely so regression
  tests exercise the real dialog rather than a copied test-only wrapper.
- Read-only metadata not being an independent D-pad focus target is not itself a
  demonstrated defect when all information is visible. No such redesign was made.
- Missing remote-input registration in the timer was also a false positive:
  its existing `tvFocus()` option rows already register the dialog window.

## Tests and reviewed images

18 new regression cases:

- `SleepTimerDialogLayoutTest`: 4 languages × large-font reachability / 48 dp targets.
- `QualityDetailsLayoutTest`: 4 languages × portrait / landscape with actual 200% system font.
- `TvConfirmationDialogInputTest`: 2 additional timer cases, including reopening.

The focused run passes all 27 cases (including 9 existing TV-dialog regressions),
and detekt passes. Three new Ukrainian screenshot references were visually reviewed:

- `app/src/test/screenshots/sleep_timer_landscape_large_uk.png` — scrolled to the lower actions.
- `app/src/test/screenshots/quality_compact_large_uk.png`.
- `app/src/test/screenshots/quality_landscape_large_uk.png`.

Only these three references were recorded in this pass; the preceding 55 were not re-recorded.

Authoritative negative timer run:
`build/legal-audit/player-dialogs-real-font-before-2026-10-18.log`
(4 timer layout failures and 1 timer TV focus failure; quality layout cases pass).

Focused successful run:
`build/legal-audit/player-dialogs-verified-2026-10-18.log`.

Full regression/build result is recorded in
`build/legal-audit/player-dialogs-final-gate-2026-10-18.log`.
Final verification: **BUILD SUCCESSFUL**, 6m 9s.

| Check | Result |
| --- | --- |
| `:app:verifyRoborazziDebug` | 2,773 tests, 0 failures/errors/skips; 58 screenshot references verified |
| `:app:testReleaseUnitTest` | 2,338 tests, 0 failures/errors/skips |
| `:app:lintDebug` | 0 errors, 0 warnings, 2 existing informational hints |
| `:app:detekt` | Passed; baseline/suppressions not expanded |
| `:ffmpegdecoder:verifySoftwareDecoderNative` | Passed |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | Passed; instrumentation APK built, not executed |
| Architecture boundaries / locale-format checks | Passed |
| Targeted `git diff --check` | Passed |

Updated local test APK: `app/build/outputs/apk/debug/app-universal-debug.apk`.
Package `com.uacastplayer.debug`, unchanged version name `0.9.7`, universal version
code `164`. This is a debug artifact, not a new signed public release.

## Limits

- ADB returned no connected devices at both checks in this pass. These are local
  Robolectric/Compose tests and rendered screenshots, not a physical phone/TV run.
- No new background work, network requests, image assets in the shipped UI or animations.
  Scrolling state exists only while its dialog is composed.
- Hisense VIDAA interoperability, remaining decoder runtime acceptance and legal/release
  readiness gates are unchanged. This is not a claim that the complete app is bug-free
  or ready for publication.
