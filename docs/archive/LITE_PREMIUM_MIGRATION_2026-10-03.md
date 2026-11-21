# Lite / one-time Premium migration

Task started 2026-10-02 and continued 2026-10-03. Scope: remove multiple paid plans and keep Lite
without purchase plus Premium with every implemented capability after one purchase.

## Implemented model

- A fresh install starts in Lite. No automatic trial, monthly/yearly sale or separate feature
  package is offered. Trial creation/eligibility code has been removed.
- Lite retains local playback, one playlist, EPG, favourites, themes, Chromecast/remux and PiP.
- The only current product is `premium_lifetime`, a non-expiring one-time in-app purchase. Its ID
  is deliberately unchanged so existing purchases can still be identified/restored.
- Premium grants all capabilities together. The UI lists only implemented paid features, not
  reserved enum entries that have no implementation.
- Store/catalogue availability never grants unpaid Premium. Cached paid access survives an
  unavailable store; a complete authoritative ownership removal/refund returns to Lite.
- Premium is shown in Settings even before payment is enabled. An empty/unpriced catalogue has
  no buy button, but restoration and its outcome remain visible.
- Existing Premium users are not offered the same purchase again. Prices come from the provider,
  not hard-coded production prices. Screenshot prices are test fixtures only.
- The purchase card uses a full-width action so the one-purchase terms are not clipped on a
  320-dp phone. Text has been updated in Ukrainian, English, Russian and Spanish.

## Compatibility and lifecycle

`MONTHLY`, `YEARLY`, `TRIAL`, `BETA` and `ADMIN` enum names are retained only to decode older records
without breaking their MAC verification. Removing those serialized names would orphan existing
records. Migration occurs after decoding/verification:

- Old paid licences become the current Premium model while keeping their original source and
  expiry. Legacy subscription IDs are accepted for ownership restoration but rejected for sale.
- Old unpaid trial/tester/admin records become Lite. They cannot create a new Premium grant.
- Time-limited legacy access has a scope-owned expiry timer and is rechecked on Activity start.
  Ownership changes cancel obsolete timers; a legacy timer cannot revoke a new permanent purchase.
  Refresh after clock rollback does not restart an unchanged timer. New non-expiring Premium has
  no expiry timer.
- Existing Play subscription renewal/cancellation is not changed remotely by this code. Stop new
  subscription sales in Console separately if those products were previously configured.
- The migration does not delete playlists, favourites or other user data.

## Additional defects corrected

1. A stable `FeatureGate` could keep already-composed lock badges stale after a purchase/refund,
   even though the click handler read the new licence. Both new regressions failed before the fix;
   the provided gate is now invalidated by entitlement changes.
2. Legacy timed access could remain active while the app stayed open because no production caller
   refreshed its expiry. Expiry now publishes Lite without navigation/restart, with cancellation
   and clock-rollback regressions.
3. A generic purchase failure could incorrectly claim no charge after a timeout with a late store
   callback. It now advises restoration and checking Google Play before another payment, without
   displaying SDK account/debug messages.

## Validation

The final gate after migration and subsequent cleanup completed **BUILD SUCCESSFUL in 10m 42s**:

- Debug unit/UI + screenshot verification: **2,411**, no failures/errors/skips.
- Release and Play: **2,135 each**, no failures/errors/skips.
- Core: unchanged successful **126-test** result reused.
- App/core detekt and all three lint variants passed. No baseline or screenshot goldens were
  weakened; lint has only the existing target-SDK hint, and R8 retains two toolchain warnings.
- All 15 additional repository checks passed.
- Final Mi A2 / Android 11 native run: **OK (34 tests), 138.393s**. This includes five licensing
  cases (single purchase, offline/refund, signed migration, timed expiry, ViewModel release),
  player/restore/video-fit, TV-style modal and loopback remote regressions.

Original debug data was restored, with no change to the primary installed application. Earlier
interrupted/failing attempts are not counted as successful runs. Debug checkout uses no real
payment account, and loopback/synthetic stream tests do not certify real TV playback.

The final universal signed APK remains **0.9.7 / 164**, at
`app/build/outputs/apk/release/app-universal-release.apk`, SHA-256
`BDF4D9999511433D133770842FF61B9932CCA11FC601C80B5058F13BD7ABFD2E`.
Signature and packaged assets were verified. See
[code cleanup and stabilization](CODE_POLISH_STABILIZATION_2026-10-03.md) for the full gate,
device log identity, APK certificate/hash and three additional provider/ownership defects fixed
without changing the Lite/Premium model.

## Payment limitation

`PremiumAvailability.STORE_IS_LIVE` remains **false**. Actual checkout is not enabled in this APK.
Activate/price the single one-time product in Play Console and verify a signed testing-track build
with a licence tester before switching real billing on. Debug checkout proves the app's state and
storage wiring without charging money; it is not a real Google Play payment test.

No release publication, version increment or replacement of the phone's primary application was
performed for this task. The universal APK is a local verification artifact, not a published update.
