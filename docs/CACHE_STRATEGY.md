# Cache strategy

Every persistent cache in the app follows the same shape: atomic writes (`AtomicFile`, or
`AtomicFile`-via-JSON for favorites), a version tag so the format can change without crashing on
old data, and never a raw URL or device identifier on disk - only SHA-256 fingerprints.

| Cache | Location | Format | Limit | Eviction |
|---|---|---|---|---|
| Playlist snapshot | `filesDir/playlist_snapshot.bin` | Versioned binary (`PlaylistSnapshotCodec`) | 8MB download cap | Whole-file replace on next successful load |
| EPG snapshot | `filesDir/epg_snapshot.bin` | Versioned binary, gzip document stored **as-is** | 96MB download cap | Whole-file replace on next successful load |
| Channel icons | `filesDir/icon_cache/` | Raw validated bytes, one file per `SHA-256(url)` | 256MB / 20,000 files, 5MB per icon | LRU by file `lastModified`, via `IconCacheTrimmer` |
| Icon failure memory (permanent) | SharedPreferences `uacast_icon_failures` | key → timestamp | - | 7-day TTL (`IconFailurePolicy`) |
| Icon failure memory (transient) | In-memory `ConcurrentHashMap` | key → timestamp | - | 1-hour TTL, process lifetime only |
| Cast incompatibility memory | SharedPreferences `uacast_cast_incompatibility` | key → timestamp | - | 30-day TTL (`IncompatibilityMemoryPolicy`), debounced writes |
| Favorites | `filesDir/favorites.json` | JSON array (hand-rolled `MiniJson`, not `org.json`) | - | User-driven (add/remove) |
| Coil image cache | `filesDir/coil_cache/` | Coil's own disk cache | 128MB | Coil's own LRU |

## Why two icon caches?

`data/icons/IconDiskCache` is *our* cache: it's where `IconRepository` looks first and what the
priority chain (tvg-logo → EPG icon → CDN-by-tvg-id) writes into after magic-byte validation. Coil's
disk cache is separate and smaller - it's Coil's own bookkeeping for whatever it decodes, not a
substitute for the validation/priority logic above. `AsyncImage` in the UI is always pointed at a
`File` our repository already resolved; Coil never fetches a channel icon URL on its own.

## Why gzip is stored as-is

The EPG document can be tens of megabytes uncompressed. Storing it gzipped on disk (inflating only
in memory at parse time via `GZIPInputStream`) keeps the on-disk footprint small and avoids paying
the decompression cost until the data is actually needed - which, on a cold app start with a
restored snapshot, is exactly once.

## Why never raw URLs

Every cache above that persists a fingerprint instead of a URL does so because that cache's job is
"have we seen this before", not "what was it" - the icon disk cache and the favorites store are the
two exceptions, because reconstructing playback requires the real URL and there's no way around
that. Logging follows the same rule: `AppLog` is a no-op in release builds and callers are expected
to never format a raw URL into a log message even in debug.
