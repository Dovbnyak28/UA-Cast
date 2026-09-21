# Cache strategy

Persistent stores use atomic writes (`AtomicFile`, or `AtomicFile`-via-JSON for favorites) and
version tags so formats can change without crashing on old data. Lookup-only stores keep SHA-256
fingerprints instead of provider URLs. The two stores that must restore playback - saved playlist
sources and parsed playlist snapshots - necessarily keep the original source/stream URLs in
app-private storage; Android backup is disabled and the privacy policy discloses this explicitly.

| Cache | Location | Format | Limit | Eviction |
|---|---|---|---|---|
| Saved playlist sources | `filesDir/playlist_sources.bin` | Versioned binary (`PlaylistSourceCodec`), including source URLs | 20 sources | User-driven (add/remove) |
| Playlist snapshot | `filesDir/playlist_snapshot_<sourceId>.bin` | Versioned binary (`PlaylistSnapshotCodec`), including source and stream URLs | 8MB source-document cap | Whole-file replace on next successful load |
| EPG snapshot | `filesDir/epg_snapshot.bin` | Versioned parsed guide (`EpgSnapshotCodec` v2); v1 raw XMLTV remains migration-readable | 96MB source-document cap | Whole-file replace on next successful load |
| Channel icons | `filesDir/icon_cache/` | Raw validated bytes, one file per `SHA-256(url)` | 256MB / 20,000 files, 5MB per icon | LRU by file `lastModified`, via `IconCacheTrimmer` |
| Icon failure memory (permanent) | SharedPreferences `uacast_icon_failures` | key → timestamp | - | 7-day TTL (`IconFailurePolicy`) |
| Icon failure memory (transient) | In-memory `ConcurrentHashMap` | key → timestamp | - | 1-hour TTL, process lifetime only |
| Cast incompatibility memory | SharedPreferences `uacast_cast_incompatibility` | key → timestamp | - | 30-day TTL (`core/cast/IncompatibilityMemoryPolicy`), debounced writes |
| Favorites | `filesDir/favorites.json` | JSON array (hand-rolled `MiniJson`, not `org.json`) | - | User-driven (add/remove) |
| Coil image cache | `filesDir/coil_cache/` | Coil's own disk cache | 128MB | Coil's own LRU |

## Why two icon caches?

`data/icons/IconDiskCache` is *our* cache: it's where `IconRepository` looks first and what the
priority chain (tvg-logo → EPG icon → CDN-by-tvg-id) writes into after magic-byte validation. Coil's
disk cache is separate and smaller - it's Coil's own bookkeeping for whatever it decodes, not a
substitute for the validation/priority logic above. `AsyncImage` in the UI is always pointed at a
`File` our repository already resolved; Coil never fetches a channel icon URL on its own.

## Why the EPG snapshot stores parsed data

The EPG document can be tens of megabytes uncompressed. Snapshot format v1 stored that source
document and paid the inflate/XML-parse/index cost again on every cold start. Format v2 stores the
already parsed `EpgData`, cutting the measured 250,000-programme restore from 53 seconds to 6.6
seconds. A v1 snapshot is still read once after upgrade and immediately rewritten as v2; see
`EpgSnapshotCodec` and `docs/PERFORMANCE.md`.

## Where raw URLs are retained

Saved playlist sources, playlist snapshots and favorites retain the real URLs required to restore
or start playback; channel-logo cache *filenames* and failure/incompatibility memory use
fingerprints because they only need stable lookup keys. These files live in app-private storage,
automatic Android backup is disabled, and deleting the app removes them.

Logging is a separate boundary: `AppLog` always records a sanitized message in the in-memory
diagnostics buffer, including in release builds, but writes Logcat only in debug builds.
`LogSanitizer` removes URL paths, credentials and token-shaped values before either sink sees the
message.
