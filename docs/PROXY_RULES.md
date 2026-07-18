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

`cast/TsProgramInfoParser.kt` reads a stream's first TS segment and walks its PAT → PMT to find the
actual video/audio stream types (see `docs/CAST_PLAYBACK_RULES.md` for how this feeds the
direct-vs-proxy decision, and the "Raw TS remux" section below for the other thing it gates). It
only handles PAT/PMT sections that fit in a single 188-byte packet - true for essentially every
real broadcast, since these tables are deliberately tiny.

## Raw TS remux

Some IPTV origins serve a channel as one continuous raw MPEG-TS stream over plain HTTP - not an
HLS playlist at all. Chromecast's Default Receiver generally won't play that directly even when
the codecs inside are otherwise fine (H.264/AAC): it's a *container* problem, not a codec one, so
unlike the passthrough case above, remuxing on the phone actually fixes it instead of just
re-serving the same unplayable bytes.

Activation (`proxy/RawTsRemuxActivation.kt`, evaluated once per playlist-resource request in
`ProxyServer.servePlaylistOrMediaResource`) requires all of:

- the response isn't already a recognized HLS playlist (`PlaylistDetector`),
- its first bytes actually look like MPEG-TS (0x47 sync bytes 188 bytes apart),
- `cast/CastCompatibilityPolicy` classifies the codecs (probed the same way as the Cast pre-flight
  check, via `cast/TsProgramInfoParser`) as `Compatible` - an incompatible codec would still fail
  after remuxing, so this path only ever fixes the container, never the codec,
- `AppPreferences.rawTsRemuxEnabled` is on (default true - an escape hatch in case keyframe
  detection turns out unreliable on some real broadcast; switching it off falls back to the
  previous plain passthrough behavior with no release needed).

When active, `ProxyServer` hands the still-open upstream response to a `RawTsRemuxSession`, which
owns a dedicated background thread for the rest of that channel's lifetime:

- `proxy/TsSegmenter.kt` reads the raw TS byte stream (resyncing to packet boundaries the same way
  the diagnostics above do - a live HTTP stream isn't guaranteed to start exactly on a sync byte)
  and cuts it into ~5-second segments, aligned to the first video keyframe (the adaptation field's
  `random_access_indicator`) at or after the target duration so every segment is independently
  decodable. If no keyframe ever shows up, a segment is still force-cut at 2x the target so the
  buffer never sees an unbounded segment.
  - **PCR clock source**: the segmenter reads the wall-clock-independent stream time it uses for
    all of the above from the PMT's declared `PCR_PID`, not from the video PID - a real broadcast
    is free to carry PCR on a separate PID (or on audio), and assuming it's on the video PID makes
    elapsed time freeze forever the moment that's wrong, either starving segmentation (unbounded
    buffer growth) or force-cutting every packet. Until the PMT has been parsed, PCR from any PID
    is accepted (matching pre-fix behavior); once resolved, only the declared PID counts, and if
    the PMT explicitly declares no PCR at all (`PCR_PID` = `0x1FFF`), no PID is ever accepted as a
    PCR source again for that program.
  - **Byte safety valve**: independent of the PCR clock, `maxSegmentBytes` (4MB default) force-cuts
    a segment the instant it would exceed that size, even mid-frame with no keyframe or PCR signal
    at all. This is the actual OOM backstop - the keyframe/duration logic above assumes a working
    clock, and this doesn't.
- `proxy/RemuxSegmentBuffer.kt` keeps a sliding window of the most recent segments, capped at 20MB
  total, evicting the oldest first.
- `proxy/LiveHlsPlaylistBuilder.kt` turns the buffer's current contents into a live (no
  `#EXT-X-ENDLIST`) HLS media playlist on every request - `#EXT-X-MEDIA-SEQUENCE` tracks the oldest
  segment still in the window, exactly per the HLS spec.

The channel's normal playlist URL (`/hls/<token>/<resourceId>`) serves this generated playlist
instead of an upstream fetch once remuxing is active; segments are served from
`/hls/<token>/<resourceId>/seg<N>.ts`, read straight out of the in-memory buffer. Only one remux
session runs per proxy session (same rule as "one active channel"), and switching channels or
stopping the proxy always tears down the old one - the reader thread's blocking read is unblocked
by closing the upstream response, not by any polling.
