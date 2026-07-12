# Proxy rules

The local HLS proxy (`data/cast/ProxyServer.kt`) exists for one reason: some Cast receivers won't
play a stream directly (wrong codec, geo/VPN restriction, TLS quirks) even though the phone can.
When that happens, the phone re-serves the stream to the receiver over the LAN instead.

## Server

- A plain `ServerSocket` bound to `0.0.0.0` on a random port (the receiver is a different device on
  the LAN, so `127.0.0.1` won't reach it - see `LocalNetworkAddress` for how the phone's own IPv4
  address is found).
- One accept thread, a fixed 6-thread pool for connection handling.
- Only `GET` and `HEAD` are served; anything else gets `405`.
- Request headers are capped at 16KB; anything larger gets rejected rather than parsed.
- Every path is `/hls/<sessionToken>/<resourceId>`, where `resourceId = SHA-256("type:url")` -
  never the raw URL. The session token changes every time the proxy (re)starts.
- The resource table is an LRU map capped at 512 entries (`LinkedHashMap` with access-order
  eviction) - old entries are simply forgotten, not actively invalidated.

## Rewriting

`proxy/M3u8Rewriter.kt` handles every reference an HLS playlist can make:

- A bare line (segment/sub-playlist URI) is resolved against the **final URL after redirects**
  (not the URL originally requested - a redirect to a different host is common) and rewritten to a
  local URL.
- A `URI="..."` attribute (on `EXT-X-KEY`, `EXT-X-MEDIA`, `EXT-X-MAP`, etc.) is rewritten the same
  way, in place, leaving the rest of the tag untouched.
- A reference whose scheme isn't `http`/`https` (most commonly `skd://` FairPlay key URIs) is left
  **unrewritten** - we can't proxy it, and rewriting it to a broken local URL would be worse than
  leaving it alone.
- Nested playlists (multi-bitrate variants) get registered as `playlist` resources recursively;
  everything else is `media` and is streamed through as raw passthrough, `Range` header included,
  without ever being buffered fully into memory.

## Session lifetime

- A `PARTIAL_WAKE_LOCK` (hard-capped at 10 hours - see `CastWakeLocks`) and a
  `WIFI_MODE_FULL_HIGH_PERF` Wi-Fi lock are held for exactly as long as the proxy is serving the
  receiver.
- Both are released the moment the proxy session ends - on a normal `CloseProxySession` signal
  (session disconnect, playback finishing, or a hard error), **not just** on a full app stop. A
  proxy session that outlives its purpose is a battery leak.

## Diagnostics

`proxy/MpegTsSniffer.kt` reads a stream's first TS segment and walks its PAT → PMT to find the
actual video/audio stream types (see `docs/CAST_PLAYBACK_RULES.md` for how this feeds the
direct-vs-proxy decision). It only handles PAT/PMT sections that fit in a single 188-byte packet -
true for essentially every real broadcast, since these tables are deliberately tiny.
