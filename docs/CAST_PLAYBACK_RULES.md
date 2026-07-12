# Cast playback rules

## Direct-first

Every cast attempt starts direct: the receiver is handed the origin stream URL, no relay involved
- it's faster and puts no load on the phone. The only exception is a (stream, receiver) pair
already known to fail (see "Incompatibility memory" below), which skips straight to the proxy.

## The two reducers

All Cast state transitions go through exactly two pure reducers (`cast/CastLoadResultReducer.kt`,
`cast/CastReceiverStatusReducer.kt`) - `CastSessionRepository` is the only thing that calls real
GMS Cast APIs or starts timers; it always ends up producing a `CastLoadResult` or `ReceiverStatus`
and feeding it through one of these two.

- **Load result reducer** - success pauses the local player; failure records an incompatibility
  signal and resumes local playback (the *caller* decides whether "resume local" actually means
  "try the proxy instead" - see below).
- **Receiver status reducer** - maps `BUFFERING`/`PLAYING`/`PAUSED`/`IDLE`+reason onto side
  effects. `PLAYING` pauses the local player. `IDLE` with `ERROR` records incompatibility, closes
  any proxy session, and resumes local playback. A synthetic `DISCONNECTED` status (session lost)
  is where the handoff back to local playback happens, including applying any channel switch that
  was requested (and queued, not applied) while casting was active.

## Watchdog

A stream that's geo-restricted or VPN-only often doesn't error out on the receiver - it just
buffers forever. So after a direct load, if the receiver isn't `PLAYING` within **4 seconds**, the
app falls back to the proxy silently (`CastSessionRepository.loadDirectWithWatchdog`). This is
raced against `proxy/MpegTsSniffer`-based diagnostics fetching the stream's first segment; if that
comes back with a known-unsupported codec (MPEG-2 video, MP2 audio - see
`proxy/TsCompatibilityPolicy.kt`) the fallback fires immediately rather than waiting out the full
4 seconds.

## Incompatibility memory

A (stream-fingerprint, receiver-fingerprint) pair that fails - direct fails **and** the proxy also
fails - is remembered on disk (`data/cast/IncompatibilityMemoryStore.kt`, keyed by
`SHA-256("streamUrl|receiverId")`, never the raw URL) for **30 days**
(`cast/IncompatibilityMemoryPolicy.kt`). The next time that exact pair is cast, `CastDeliveryStrategy`
starts straight on the proxy instead of wasting 4 seconds re-discovering the same failure. Writes
are debounced (minimum 2 seconds apart) so a flaky reconnect loop can't hammer the store.

## Pending channel switch

Switching channels while casting doesn't touch the local player (it's paused/idle for the whole
cast session) - it immediately re-loads the new channel on the receiver, *and* queues the index as
"pending". When the session eventually disconnects, the local player catches up to whatever
channel was last requested during the cast session, not whatever it happened to be paused on when
casting started.
