# UA Cast Player

A personal Android IPTV player: import an M3U playlist, watch channels locally, cast to
Chromecast/Google TV, and browse a channel guide (XMLTV/EPG) with logos.

## Features

- **Playlists** — import M3U by URL or file (SAF), automatic group normalization with a
  multilingual alias dictionary, a versioned on-disk snapshot so the last playlist survives a
  restart.
- **Playback** — a single ExoPlayer instance per session, FFmpeg-backed decoder fallback for
  IPTV-typical audio codecs (MP2/AC-3/DTS), automatic retry with backoff on transient errors,
  Picture-in-Picture, fullscreen, and a next-channels preview carousel.
- **EPG** — XMLTV guide with a hardened SAX parser, channel-name matching (tvg-id → tvg-name →
  normalized display name), live "now playing" progress bars.
- **Channel logos** — tvg-logo → EPG icon → CDN-by-tvg-id priority chain, magic-byte validated
  disk cache, background Wi-Fi-gated prefetch.
- **Cast** — direct-to-receiver playback first; if the receiver can't play a stream directly
  (common with geo-restricted/VPN-only feeds or incompatible codecs), the app falls back to a
  local HLS relay running on the phone, automatically and silently.
- **Favorites and settings** — per-channel favorites, icon display mode, list density/layout,
  Wi-Fi-only prefetch, cache management, and defaults driven by a lightweight device performance
  classifier (always overridable by the user).
- **Languages** — Ukrainian, English, Russian, Spanish, selectable on first run or from Settings.

## Stack

Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL with a version catalog
(`gradle/libs.versions.toml`), media3/ExoPlayer, `nextlib-media3ext` (FFmpeg decoder extensions),
Play Services Cast framework, OkHttp, Coil.

Baseline profile: `app/src/main/baseline-prof.txt` contains a real generated per-method capture plus
broad app/media3 fallback rules. The `:baselineprofile` module now covers first-run screens and, via
a credential-free fixture compiled only into throwaway variants, Channels, player, fullscreen and
EPG. It also carries deterministic Macrobenchmarks for cold/warm startup, a 40,000-channel playlist
and a 350,000-programme XMLTV parse; see `docs/RELEASING.md` for device requirements and commands.
Coil and Compose ship their own profiles inside their AARs and are merged automatically.

## Building

```
./gradlew :app:compileDebugKotlin   # compile
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # lint
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (unsigned unless signing env vars are set)
```

Release signing is read only from environment variables / Gradle properties, never committed:
`UACAST_STORE_FILE`, `UACAST_STORE_PASSWORD`, `UACAST_KEY_ALIAS`, `UACAST_KEY_PASSWORD`.

`minSdk` is 24 (Android 7.0). The Cast proxy session runs as a `mediaPlayback` foreground service
(`FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission), so a physical or emulated device/receiver is
needed to exercise casting end-to-end; unit tests and lint don't touch that path.

To package the source tree without `.git`/`.gradle`/`build` (a checkout can run ~450 MB; the
tracked source is a couple MB): `git archive --format=zip -o uacast-source.zip HEAD`.

## Project layout

Business logic is organized by feature as plain Kotlin (no Android/Compose types), each paired
with unit tests, under `app/src/main/kotlin/com/uacastplayer/`:

- `playlist/`, `epg/`, `icons/`, `player/`, `cast/`, `proxy/`, `favorites/`, `performance/` — pure
  parsers, reducers, and policy objects.
- `data/*` — the Android-dependent glue (repositories, disk stores, network clients) that drives
  the pure logic above from real I/O.
- `ui/*` — Compose screens; they render state, they don't decide it.

See `docs/` for the design rules behind the trickier subsystems:

- [`PROXY_RULES.md`](docs/PROXY_RULES.md) — the local HLS relay used for Cast fallback.
- [`CAST_PLAYBACK_RULES.md`](docs/CAST_PLAYBACK_RULES.md) — direct-vs-proxy delivery, the
  watchdog, and incompatibility memory.
- [`CACHE_STRATEGY.md`](docs/CACHE_STRATEGY.md) — every on-disk/in-memory cache in the app, its
  limits, and its eviction policy.
- [`EPG_STRATEGY.md`](docs/EPG_STRATEGY.md) — XMLTV parsing, matching, and current/next lookup.

## Known limitations

- The five `epg.it999.ru` EPG source variants (`EpgSource.kt`) and the built-in
  `https://cdn.epg.one/logo/` icon fallback are configured in source. Availability still depends on
  those third-party services and should be included in release smoke testing.
- Built and verified via `gradlew` command-line builds, plus manual on-device testing (language
  picker, all four tabs, EPG source download for both the gzip and plain-XML variants) on a Xiaomi
  Mi A2 (Android 11). Cast hardware, PiP, and orientation changes haven't been exercised on a real
  receiver/device yet.

## CI

`.github/workflows/android-ci.yml` runs `assembleDebug` → `testDebugUnitTest` → `lintDebug` →
`assembleRelease` (unsigned) on every push/PR, plus Gradle wrapper validation and
`gradle/actions/setup-gradle` caching. `versionCode`/`versionName` for the CI-built APKs are
derived from the workflow run number (`-Puacast.versionCode`/`-Puacast.versionName`), not the
hand-edited defaults in `app/build.gradle.kts`.
