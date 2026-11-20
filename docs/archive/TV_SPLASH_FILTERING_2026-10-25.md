# TV splash logo filtering — 2026-10-25

## Confirmed cause and fix

The startup theme referenced a 720×720 PNG stored only in `drawable-xxxhdpi`.
Robolectric native-graphics regression tests reproduced density decoding to
180×180 (TV mdpi), 240×240 (TV tvdpi), and 360×360 (TV xhdpi), before the splash
renderer scales it to its final bounds. This loses detail unnecessarily on TV.

The theme still references `@drawable/splash_logo`. It is now a bitmap drawable
with explicit filtered scaling, backed by `splash_logo_artwork`:

- `drawable-xxxhdpi/splash_logo_artwork.png` retains phone density decoding.
- `drawable-television-nodpi/splash_logo_artwork.png` retains all 720×720 pixels
  for the final TV scale.
- `drawable/splash_logo.xml` enables bitmap filtering and anti-aliasing.

The original artwork and transparent mask-safe margins are unchanged. Both PNGs
have the original SHA-256:
`101DB5275B0055EF06C487D446DBD0A3AF48298D445629DB09A5AD05BCEEC210`.
The TV source bitmap requires 2,073,600 bytes, below the tested two-MiB bound.
No animation, background task, or additional persistent cache was introduced.

Lint detects the intentionally identical pixels across these density variants.
`app/lint.xml` excludes only these two files from `IconDuplicatesConfig`, with
the density/memory rationale documented. The check remains enabled elsewhere.
Using one global nodpi asset would also increase phone decoding memory.

## Verification

- Before the fix: five of six new tests failed; the phone control passed.
- After the fix: six splash tests and six existing TV launcher-banner tests pass
  in both debug and release variants: **24 executions, zero failures/skips**.
- Two new native-rendered screenshot references at 384×384 and 576×576 were
  visually inspected and verified. They cover the logo, not the whole system
  splash-screen window.
- `:app:lintDebug`: zero errors/warnings; 27 informational hints remain.
- `:app:assembleDebug`: successful. Final run log:
  `build/legal-audit/tv-splash-checked-2026-10-25.log`.
- `aapt2 dump resources` confirms the shared XML plus xxxhdpi and
  television-nodpi PNG variants in the universal debug APK.
- The focused source diff passes `git diff --check`.

Output: `app/build/outputs/apk/debug/app-universal-debug.apk`.

## Remaining hardware verification

ADB reported no connected device during this change, so no physical TV
installation or visual check is claimed. Confirm the cold-start splash on Mi TV
at its actual display/compositor resolution. The fix avoids app-side loss of
source detail; it cannot change the TV firmware's panel scaling.
