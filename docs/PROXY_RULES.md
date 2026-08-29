# Proxy rules

The local HLS proxy (`data/cast/ProxyServer.kt`) exists for one reason: some Cast receivers won't
play a stream directly (wrong codec, geo/VPN restriction, TLS quirks) even though the phone can.
When that happens, the phone re-serves the stream to the receiver over the LAN instead.

## Known limitation: IPv6-only networks

The proxy is IPv4-only end to end - `LocalNetworkAddress.currentIpv4Address` is the only address a
receiver on the same LAN could plausibly reach, and there's no IPv6 equivalent implemented. On a
network that genuinely has no IPv4 address for the phone (IPv6-only, no NAT64/DHCP fallback), a
proxy fallback is not just harder, it's impossible - no address exists to hand the receiver. This is
detected explicitly rather than left to fail silently: `CastSessionRepository` shows
`cast_proxy_ipv4_unavailable_message` ("Backup streaming isn't available on this network") instead
of quietly recording a false incompatibility or bouncing to local playback with no explanation - and
critically, the direct-mode attempt already in flight is left running rather than being cancelled in
favor of a fallback that could never have worked on this network anyway.

## Server

- A plain `ServerSocket` bound to `0.0.0.0` on a random port (the receiver is a different device on
  the LAN, so `127.0.0.1` won't reach it - see `LocalNetworkAddress` for how the phone's own IPv4
  address is found).
- One accept thread, a bounded 4-thread admission pool for request parsing/authentication, then a
  bounded 16-thread response pool. The split prevents slow or unauthorised request headers from
  occupying media-serving workers; both queues reject overload instead of growing without bound.
- Only `GET` and `HEAD` are served; anything else gets `405`.
- Request headers are capped at 16KB; anything larger gets rejected rather than parsed.
- Every path is `/hls/<sessionToken>/<resourceId>`, where `resourceId = SHA-256("type:url")` -
  never the raw URL. The session token is scoped to a whole cast session (`CastSessionRepository`
  generates one per connect, not per channel), not to a single load - a mid-session channel switch
  calls `ProxyServer.ensureStarted` with that same token and is a no-op if the server is already
  running for it, reusing the same port/resources/remux session; only an actual new cast session or
  a host change (a genuinely different `(sessionToken, host)` pair) forces a real restart via
  `ProxyServer.start`, which tears everything down and rebinds a fresh port. Calling `start`
  directly for a mid-session switch - the previous behavior - meant every channel switch during a
  proxy-mode cast session silently invalidated the URL the receiver was still fetching from.
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
  leaving it alone. The shared resolver also returns such references as unresolvable to flatten,
  wrapper-unwrapping and diagnostic callers, so none can accidentally hand a non-HTTP URI to OkHttp.
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
- `ProxyServer.ensureStarted()` (the `ServerSocket` bind + thread pool - cheap, no wake locks
  involved, a no-op if already running for the current session) runs the moment any Cast session
  connects, not lazily on the first fallback - so if a fallback does turn out to be needed, it isn't
  also paying for server startup at that point. This
  is separate from (and doesn't advance) the foreground-service/wake-lock lifecycle above, which
  still only starts once a fallback is actually decided - a purely-direct session's eagerly-started
  proxy just sits idle, unused, and gets torn down by the same unconditional `CloseProxySession` on
  disconnect as any other.

## Diagnostics

`core/cast/TsProgramInfoParser.kt` reads a stream's first TS segment and walks its PAT → PMT to find the
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
- `core/cast/CastCompatibilityPolicy` classifies the codecs (probed the same way as the Cast pre-flight
  check, via `core/cast/TsProgramInfoParser`) as `Compatible` - an incompatible codec would still fail
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
  - **Startup ramp**: the first 2 segments cut at a shorter 2s target instead of the usual 5s -
    `awaitInitialPlaylist` blocks until enough segments exist for a non-empty playlist, so shorter
    early segments get a cast session's very first playlist (and thus playback) noticeably sooner,
    at the cost of a couple of smaller-than-usual segments only at the very start of the channel.
- `proxy/RemuxSegmentBuffer.kt` keeps a sliding window of the most recent segments, capped at 48 MiB
  total, evicting the oldest first.
- `proxy/LiveHlsPlaylistBuilder.kt` turns the buffer's current contents into a live (no
  `#EXT-X-ENDLIST`) HLS media playlist on every request - `#EXT-X-MEDIA-SEQUENCE` tracks the oldest
  segment still in the window, exactly per the HLS spec.

The channel's normal playlist URL (`/hls/<token>/<resourceId>`) serves this generated playlist
instead of an upstream fetch once remuxing is active; segments are served from
`/hls/<token>/<resourceId>/seg<N>.ts`, read straight out of the in-memory buffer. Only one remux
session is *active* per proxy session, and stopping the proxy always tears down every session
outright - the reader thread's blocking read is unblocked by closing the upstream response, not by
any polling.

**Handoff on a channel switch** (`proxy/RemuxHandoffPolicy.kt`, pure): a channel switch replaces the
active remux session and stops the replaced session's upstream reader immediately, so it cannot keep
a second IPTV connection and grow a second 48 MiB buffer beside the new channel. The completed
buffer remains "draining" and servable for in-flight requests (a segment fetch already issued, a
stale playlist poll) until either the new channel is confirmed loaded on the receiver
(`ProxyServer.confirmActiveSession`, called from `CastSessionRepository` on a successful load) or
10 seconds elapse, whichever comes first. Only one session ever drains at a time; a third switch
discards the older draining buffer before retaining the newly replaced one.

### Upstream reconnect

A raw-TS origin connection can drop mid-stream (network blip, an overloaded encoder resetting the
socket) without the receiver ever asking for anything different - previously this ended the
`RawTsRemuxSession` outright, silently freezing the receiver's live playlist forever. Instead,
`RawTsRemuxSession`'s reader loop treats both a clean EOF and an `IOException` while reading as "the
connection ended, not the channel" and reconnects to the same origin (same `Request`, same
`OkHttpClient`, so it shares the app-wide connection pool - see `core/net/AppHttp.kt`):

- **Backoff** (`proxy/RemuxReconnectPolicy.kt`, pure): 1s, then 2s, then 4s. A successful reconnect
  resets the attempt counter; three consecutive failures give up and end the session the same way a
  permanent origin failure always has.
- **Discontinuity**: `TsSegmenter.onReconnect()` flushes whatever was buffered before the gap as its
  own final segment, resets the PCR clock (the reconnected stream's PCR values aren't on the same
  clock as before the gap - diffing across it would produce garbage durations), but leaves
  PAT/PMT/video-PID/PCR-PID alone, since it's still the same channel. The *next* segment produced is
  marked `TsSegment.discontinuity = true`, which `LiveHlsPlaylistBuilder` turns into an
  `#EXT-X-DISCONTINUITY` tag immediately before that segment - `#EXT-X-MEDIA-SEQUENCE` numbering
  continues across the gap unchanged, per the live-HLS spec.
- `RawTsRemuxSession.stop()` interrupts the reader thread (in addition to closing the current
  response) specifically so it can't be left sleeping through a multi-second backoff wait after the
  session has actually been asked to stop.
