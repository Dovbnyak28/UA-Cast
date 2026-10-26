# Android TV modal-input stabilization — 2026-09-30

## Scope and verified findings

This follow-up traced Android TV browsing/player input through `MainActivity`,
`TvRemoteReceiverViewModel`, `dispatchTvRemote`, `TvInputRegistry`, Compose dialog windows,
settings/paywall actions and player cleanup. It is a targeted continuation, not a claim that
every line of the repository or every television firmware has been certified.

Three related **P2** defects were fixed. These affect TV input/navigation, not the encrypted
transport itself. The omitted-window cases can be dismissed with an OEM remote; the PIN-field
trap still allows BACK to exit but prevents normal vertical D-pad traversal inside the form.

### 1. Dialog windows omitted from phone-input routing

Several dialogs contained only Material `TextButton`/text-field controls and neither an
explicit `TvDialogInputRegistration` nor a custom `tvFocus` control that implicitly registered
their window. The no-store Premium sheet similarly contained read-only text and no product buttons.

Execution path:

`receiver.commands` → resumed Activity collector → `dispatchTvRemote(command, registry)` →
`dispatchToDialog` returns null → BACK calls the Activity's back dispatcher, while SELECT/
D-pad dispatch to the Activity window instead of the visible Compose dialog window.

This can navigate the obscured screen or leave a confirmation unresponsive to the phone.
Consent/PIN callbacks must not be executed by an unrelated underlying control.

Explicit window-owned registration was added to:

| File under `app/src/main/kotlin/com/uacastplayer/ui/` | Function |
| --- | --- |
| `components/SetPinDialog.kt` | `SetPinDialog` |
| `premium/UnlockDialog.kt` | `UnlockDialog` |
| `premium/PremiumBottomSheet.kt` | `PremiumBottomSheet` |
| `settings/SettingsSupportSections.kt` | `BackupExportWarningDialog` |
| `settings/SettingsAccessSections.kt` | `ResetParentalControlConfirmDialog` |
| `diagnostics/DiagnosticsSend.kt` | `DiagnosticsPreviewDialog` |

Confirmation/dismissal buttons in the affected alert dialogs also use the existing TV focus
indicator. Disabled PIN submission remains disabled and non-focusable; the PIN format, matching,
storage and verification policies are unchanged. On phones `tvFocus` and registration are no-ops.

Before fixing these windows, four alert-dialog host regressions failed at the missing registration;
the no-store Premium-sheet regression failed separately. PIN creation had the same source-level
omission, but its host renderer could not reach idle, so its routing acceptance runs natively.

### 2. Empty settings sheets lost their remote target

`HiddenGroupsSheet` and `LockedChannelsSheet` relied on `SecondaryButton.tvFocus` in their
rows to register the dialog. When the last displayed row disappeared, the row's disposable
registration was removed but the sheet remained open.

For hidden groups this is possible when referenced groups disappear/change with the playlist,
or the last matching group is restored while other hidden-group keys remain. The settings
control can also open with no matching groups at all. For locked channels, unlocking the final
matching channel changes the asynchronously filtered list to empty without dismissing the sheet.

Both windows now register independently of list contents. Pre-fix tests with the correct
settings Column layout and explicit asynchronous-list waits reproduced the missing registration
after restoring/unlocking the final displayed row.

### 3. D-pad focus trapped in PIN input fields

`SetPinDialog` and `ParentalControlPinDialog` did not use the existing
`tvTextFieldNavigation` modifier. Once an `OutlinedTextField` had focus, a phone/TV D-pad DOWN
event was handled as text/cursor input instead of moving to the next field/action. Registering
the window alone did not solve this separate problem.

Two additional tests on the real Xiaomi reproduced this before the navigation change
(2 tests, 2 failures, 13.361 seconds): DOWN did not focus the confirmation field in PIN creation,
and DOWN did not reach Cancel in PIN verification. Neither fixture entered or persisted a PIN.

All three PIN fields now use the same TV-specific Up/Down focus traversal already used by the
TV search and playlist forms. Left/Right text-cursor behavior and phone touch/IME behavior are
unchanged. Disabled confirmation buttons remain skipped. Verification buttons now use the
existing TV focus indicator, with enabled state still controlled by `ParentalControlPinPolicy`.

## Regression design and rejected false positives

- Host tests exercise the production Activity dispatcher, not just `performClick` on dialog actions.
- BACK dismisses the modal without calling the underlying screen's back handler.
- SELECT on Cancel/Later fires once, without triggering approval or the underlying control.
- Approving backup export, diagnostics or Premium fires the mock callback exactly once.
- Two stacked dialogs route to the top window; after its dismissal the older window remains
  registered and receives input. After both close, no stale window remains registered.
- Empty/no-store windows retain input; dismissal releases registration.
- PIN fields can be left with actual D-pad commands through the Activity dispatcher; Cancel
  is reachable while confirmation is disabled. Test setup asserts the starting field's focus.
- All PIN/reset/backup/billing/report callbacks are isolated test doubles; no real PIN reset,
  backup export, purchase, report upload or user-source modification is performed by these fixtures.

The local Robolectric runtime repeatedly timed out while composing the two-text-field PIN
dialog, including with a non-empty underlying screen and manually advanced virtual frames.
That is **not evidence of an application ANR**: the real Xiaomi Android 9 dialog passed the
native BACK/SELECT/dismissal contract. The same regression remains in the native suite rather
than being hidden by a relaxed assertion, increased timeout, ignored test or production workaround.
Its exact host-renderer limitation is unresolved.

An early locked-channel fixture also timed out because settings rows were overlaid rather than
wrapped in the real Column layout. Correcting the fixture and waiting for `produceState` results
then reproduced the actual empty-window registration failure. That fixture error is not listed
as a production bug. The intentional parental-control "forgot PIN" reset policy was not changed.

## Verification

The first native pass on Xiaomi Android 9/API 28 passed five new alert-dialog regressions
(24.421 seconds). The subsequent expanded final-artifact results are recorded below.

Targeted host pass: **55 tests, zero failures** across TV presentation/input, diagnostics and
Premium tests. An overlong line in the newly added test failed detekt on that invocation;
it was wrapped without relaxing the rule or changing the baseline.

Follow-up verification completed on 2026-10-02: the full debug/release/Play, screenshot,
lint/detekt and signed-APK gate passed, including the PIN traversal changes. All eight native
confirmation/PIN and three empty-modal tests passed on **Mi A2 Android 11/API 30** in an isolated
TV presentation (part of a 15-test run including artwork regressions, 19.789s). This confirms
real Android dialog routing, not Xiaomi firmware/physical-remote acceptance. The interrupted
September 30 final native Xiaomi run still has no captured final outcome and is not marked passed.
See [STABILIZATION_AUDIT_2026-10-02.md](STABILIZATION_AUDIT_2026-10-02.md) for exact results/artifacts.

## Remaining acceptance limits

No new phone-to-TV LAN gesture session is claimed from in-process key dispatch/native loopback
tests. A phone was not connected during this pass. Hardware OEM remote buttons, TV system IME,
permission/installer windows, provider codecs, Wi-Fi blackholes/reconnection and prolonged
playback still require dedicated acceptance. No Hisense VIDAA/DLNA fix is inferred from Android
TV tests. A signed release APK is not considered physically executed merely because debug passed.

No version bump, GitHub publication, user-data clearing, network/display configuration change
or computer shutdown is part of this continuation.
