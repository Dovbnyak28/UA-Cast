# User-supplied channel icon packs — 2026-10-03

## Scope

Channel logos only. Application launcher icons, the Android TV banner, navigation icons and
button icons are outside this change. Existing unrelated work is preserved.

## Current contract

- A clean installation has no configured channel-icon source.
- Playlist `tvg-logo`, EPG channel artwork and the former predefined CDN are not queried by
  the channel-logo resolver. Their metadata can still be parsed without making image requests.
- Without a user pack, or without a matching `tvg-id`, channel cards show initials.
- Users add a directory URL under Settings → Appearance and language → Channel icon packs.
  For example, `https://your-host.example/logos` plus `tvg-id="news"` requests
  `https://your-host.example/logos/news.png`. IDs are encoded as a single URL path segment.
- This preserves the existing directory-pack format; ZIP archives or JSON manifests are not
  implemented by this change. Pack files must use the playlist's channel IDs.
- Several user packs are tried in configured order for local rendering. HTTP errors, malformed
  images and oversized downloads retain existing failure handling and resource limits.
- Chromecast artwork comes only from the first explicitly added pack; receiver-side image
  availability is not verified. No playlist/EPG fallback is sent to the receiver.
- Existing custom URLs are preserved. Legacy automatic cache files are not forcibly deleted,
  but cannot be selected without a matching explicitly added pack. Normal cache cleanup remains.
- Adding/removing a pack changes a UI revision, invalidates resolution memory and retires old
  prefetch generations. Removing the last pack closes its network watcher. An in-flight response
  from a removed pack cannot publish its logo to a visible row.
- Channel lists, Home, Favorites, the player, mini-player and Android TV use the same refresh key.
- Existing Premium gating of custom icon sources remains unchanged.

## Regression coverage

Targeted coverage includes no-pack/no-request behavior, ignored legacy cache entries, Unicode
channel IDs, persistence across repository recreation, failed-first-pack fallback, pack removal
during a request, UI refresh on source edits, network-watcher ownership, prefetch interruption,
failure retry and cache-clear barriers. Existing automatic-source tests were replaced with the
new explicit-pack contract; no tests were disabled to hide failures.

Native artwork tests include real Coil PNG decoding, removing a pack revision, switching channels
while resolution waits, repaired files, Favorites refresh and retiring old artwork colour.

## Verification status

- Targeted host pass: 97 tests, 21 suites, zero failures/errors/skips; Debug lint and detekt passed.
- Full Debug pass: 2,435 tests, 404 suites, zero failures/errors/skips; screenshot verification passed.
- Release and Play: 2,154 tests and 343 suites per variant, zero failures/errors/skips.
- Core: 126 tests, 15 suites, zero failures/errors/skips; unchanged inputs reused verified results.
- All 15 repository `check-*.sh` scripts passed. `git diff --check` passed; Git only reported
  existing CRLF-to-LF normalization notices, not whitespace defects.
- Mi A2 (`263efaf`, Android 11): `ChannelArtworkInstrumentedTest` (5),
  `PlayerLifecycleInstrumentedTest` (15), and `PlayerRestorationRegressionInstrumentedTest` (2)
  passed: **22 tests**, runner duration 86.957 seconds. The preservation runner restored the
  original debug app's files/preferences. The primary `com.uacastplayer` app was not modified.
- Native evidence: `app/build/device-audit/instrumented-1646c52264bb40afb3d0d418cb3f2167.txt`.
  Its private recovery archive and fixture data must not be published.

The complete host gate finished successfully in 9 minutes 21 seconds (156 tasks): Core tests and
detekt, Debug screenshot verification, Release/Play unit tests, lint for all three application
variants, application detekt, and signed release assembly. No tests were skipped.

Lint reports zero errors, zero warnings and one informational `OldTargetApi` hint per variant
for the unchanged `targetSdk = 36`. R8 emitted its class-file provider warning twice:
`Class file resource provider does not support async parsing` for
`com.android.builder.dexing.r8.ClassFileProviderFactory$OrderedClassFileResourceProvider`.
These build-tool diagnostics are recorded rather than suppressed. This task does not silently
change the application's Android target or upgrade its build-tool versions.

After assembly, the release legal-assets check passed for all four APKs, version ordering passed
(universal 164 > ABI variants 161/162/163), and the generated Play manifest/BuildConfig restriction
check passed. No new Play AAB was produced; earlier AAB outputs are not this change's deliverable.

## Signed artifact

- `app/build/outputs/apk/release/app-universal-release.apk`
- Version: `0.9.7`, versionCode `164`; unchanged by this task.
- Size: 24,177,560 bytes.
- SHA-256: `850152F566F9737DF05C053CE18590FC2136C77DC89EC434C4E74897D4129DA2`.
- `apksigner verify --verbose --print-certs`: passed, APK signature v2/v3 verified, one signer.
- Signing certificate SHA-256:
  `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`, matching the existing release key.

No release publication or version bump is part of this change. The signed production APK has not
been installed on the phone. A real Chromecast receiver and an external user-provided icon pack
have not been tested in this pass; receiver artwork policy is covered by host tests and pack HTTP
behavior by controlled loopback origins. The result establishes the changed logo contract and
the tested regressions, not a guarantee that the complete application has no remaining bugs.
