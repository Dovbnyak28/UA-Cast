# Code cleanup and stabilization — 2026-10-03

## Scope

This pass follows the requested order: trace current behavior, reproduce failures, make bounded
changes, then run regression checks. It follows the Lite/Premium migration rather than deleting
compatibility records or rewriting working playback code. Existing unrelated Android TV, website,
VM and audit files in the dirty checkout are preserved.

It does not certify every code path or promise zero bugs. No dependency/version change,
publication, primary-phone data clearing or computer shutdown is part of this pass.

## Removed dead mechanisms

- `PremiumRepository.storeCanSell`, its persistent storage property/key accessor and its automatic
  sale-catalogue query: repository-wide reference search found no production consumer. Prices are
  still queried on demand for the Premium screen/checkout. Existing unused preference entries are
  simply ignored; no preference file or licence is deleted.
- `BillingProduct.offerToken`: no consumer remains after the one-time purchase migration. This
  transient UI model is not serialized; removing the unused subscription offer field does not
  alter stored licences, ownership tokens or restoration.
- The permanently empty `PremiumProducts.SUBSCRIPTION_IDS` sale list. Legacy subscription product
  IDs, tier decoding and both ownership-type queries remain intact for restoration.
- Four obsolete catalogue-latch tests are replaced with behavioral checks for grants, refunds and
  repeated ownership updates without price queries. No failing behavior is hidden with ignores,
  suppressions, baseline edits or new screenshot goldens.

## Confirmed defects and corrections

### P2 — Price query can block ownership resolution

`observeProvider` received connected ownership, then awaited an unused catalogue diagnostic
before `applyPurchases`. A suspended price response delayed both a paid grant and an authoritative
refund indefinitely. Two regressions failed before removal and pass afterward. Ownership now
has no sale-catalogue dependency; repeated store events make zero unsolicited catalogue requests.

### P2 — Retired provider can overwrite current ownership

`purchase` captured provider A, suspended in its checkout, and applied A's eventual success even
after `useProvider(B)`. A delayed fake checkout reproduces the write to current persisted access.
Its before-fix test returned `Success` instead of retiring the old result. The repository now
validates provider identity and owner lifetime after suspension and before launch/publication.
Old catalogue/restore results are retired as well. Runtime provider replacement currently belongs
to the debug seam; it is not claimed as a production Google Play checkout reproduction.

### P2 — Billing SDK has no destruction boundary

Cancelling the repository observer did not release the previous provider. The Play implementation
never called `BillingClient.endConnection`, and `AppViewModel.onCleared` released only icon work.
The replacement-owner regression observed zero releases before the fix.

Providers now expose an idempotent release contract. Replacement releases the old owner;
`AppViewModel.onCleared` closes the repository, its observer/expiry timer and current provider.
The Play provider ends the SDK connection, completes the pending UI wait and rejects late
callbacks. Closing does not clear paid access or persisted user data. The real Play provider
remains disabled in the current distribution, so SDK interoperability/payment verification is
still a separate Play-track requirement, not a device result claimed here.

## Regression coverage

New host tests cover blocked prices, repeated grants/refunds, SDK-owner replacement, old checkout
completion, double close, same-provider reuse, attempted provider replacement after destruction,
late catalogue completion and paid-cache retention after close. Four core behavioral assertions
were first observed failing: two blocked ownership paths, missing release, and stale checkout.
The latter pair's XML is retained locally under ignored
`app/build/device-audit/code-polish-20261003/provider-lifecycle-before-fix.xml`.

A native regression checks that clearing a real `ViewModelStore` invokes provider release once,
with no purchase or external account. Existing migration/expiry/AndroidKeyStore tests are retained.

Focused host verification passed **131 tests**, zero failures/errors, plus app detekt. All 15
existing `check-*.sh` scripts passed: architecture, empty detekt baseline, logs, Play distribution,
premium purity/gates, version ordering, legal assets, UI restrictions and locale formatting.

## Final validation

The complete gate finished **BUILD SUCCESSFUL in 10m 42s**, with 203 tasks (52 executed,
151 up-to-date), offline and one Gradle worker:

```text
:core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:detekt
:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
-Puacast.requireSigning=true --continue --offline --max-workers=1 --no-daemon
```

| Check | Result |
| --- | --- |
| Debug unit/UI + screenshot verification | 2,411 tests, 0 failures/errors/skips |
| Release unit | 2,135 tests, 0 failures/errors/skips |
| Play unit | 2,135 tests, 0 failures/errors/skips |
| Core | 126 tests, 0 failures/errors/skips; unchanged successful result reused |
| App/core detekt | passed, empty reports; no baseline edits |
| Debug/release/Play lint | 0 errors, 0 warnings; existing `OldTargetApi` hint only |
| Debug, test and signed universal release builds | passed |
| All 15 additional repository checks | passed |

Variant counts are overlapping executions, not distinct test cases. The two existing R8
`ClassFileResourceProvider` asynchronous-parsing warnings remain toolchain diagnostics; no
suppression or incidental AGP/SDK upgrade was used to hide them. Target SDK is unchanged at 36.

### Physical Mi A2 — Android 11 / API 30

After the user unlocked the phone, the final debug/test APKs passed **OK (34 tests), 138.393s**:

- Lite/Premium, migration, AndroidKeyStore MAC, timed expiry and real ViewModel release: 5.
- TV-style empty modal ownership/input: 3.
- Player lifecycle/intent, rapid switches/toggles, background/foreground and cleanup: 15.
- Video fit controls and persistence: 4.
- Player Activity restoration and parental filtering: 2.
- Remote ownership/disconnect/cancellation: 4.
- Real platform remote key derivation compatibility: 1.

Local output: `app/build/device-audit/instrumented-ffec5b6b7e734ab0828af828cd557ed6.txt`.
The runner restored original debug `files`/`shared_prefs`; the primary `com.uacastplayer` package
was not replaced or cleared. Private recovery archives and fixture data remain local and must
not be published. A previous interrupted seven-case run was recovered before rerunning; it was
not counted as successful. Its replacement completed seven cases on the intermediate APK, but
the final 34-case result above is the relevant verification after code cleanup.

The player tests use a synthetic HLS origin and verify engine intent/state/ownership, not sustained
decoded video. Remote tests use loopback, not a physical television. Debug checkout is not a real
Google Play purchase. No new production defect was reproduced during this final native run.

### Final artifacts

- Universal signed release: `app/build/outputs/apk/release/app-universal-release.apk`.
- Version: **0.9.7 / universal versionCode 164**; 24,171,492 bytes.
- Supported ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`; TV launcher entry retained.
- SHA-256: `BDF4D9999511433D133770842FF61B9932CCA11FC601C80B5058F13BD7ABFD2E`.
- APK v2/v3 signature verification passed with one signer. Certificate SHA-256:
  `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.
- Assets inspection found only baseline-profile/public-suffix/legal/font-licence assets, no
  temporary playlist, private device logs or recovery archive.
- Tested debug APK SHA-256:
  `BA812B90584B376090196440129CA42BDF56D6137340CBEDBCD79A4C3971DA37`.
- Tested instrumented APK SHA-256:
  `F159E0617708D832E209817E56CF463F29ABAA701F51E242679143C299891720`.

This is a local verification artifact. No version increment or GitHub release was performed.

## Remaining external verification

- Actual Google Play purchase, refund, restore, offline/reconnection and legacy subscription
  ownership require the activated one-time product and a signed licence-tester track build.
- Hisense VIDAA/DLNA and Xiaomi TV interoperability require the actual television and network.
- No static/host test result substitutes for prolonged real-provider playback, OEM lifecycle,
  process-kill recovery or low-memory/device performance measurements.

The project should be stabilized incrementally against these reproducible boundaries, not
declared perfect because a cleanup gate is green.
