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

## Load generations and status classification

`CastSessionRepository` issues a second `load()` request whenever the watchdog, a codec-routing
decision, or a fast channel switch supersedes an in-flight one - the *first* request's own SDK
result callback still fires after that, often with a status code that looks like a failure (2103
`REPLACED`, or the media-queue noise of 2002) but isn't one. Every `loadOnReceiver` call increments
a monotonic `loadGeneration` counter *before* calling the SDK; a result callback whose generation no
longer matches the current one is dropped immediately (`cast load: gen=<g> status=stale
action=ignored`), and `cast/LoadStatusClassifier.kt` further tells a `Superseded` status (SDK-level
noise from being replaced, always ignored) apart from a `Rejected`/`Failed` status (a genuine
failure of that specific request, which still goes through the normal direct-fails-to-proxy path).
Every outcome logs one line: `cast load: gen=<g> status=<NAME(code)> action=<...>`.

The receiver's own status stream (`RemoteMediaClient.Callback.onStatusUpdated`) has the same
problem from a different angle: issuing a new load naturally interrupts whatever the receiver was
doing, and the SDK reports that as an ordinary `IDLE` (`CANCELLED`/`INTERRUPTED`/`NONE`), not an
error. `CastReceiverStatusReducer.reduce` takes a `selfInitiated` flag - true only for the first
status update after this app itself issued a load - and ignores that specific IDLE shape entirely
when it's set; an `IDLE` that's actually `ERROR` or `FINISHED` still goes through normal handling
even if self-initiated, since a real error is still an error.

## Stale channel guard

A watchdog timeout and a diagnostic result are both deferred continuations holding a `streamUrl`
captured in a closure - the diagnostic in particular runs on the IO dispatcher, genuinely
concurrently with the user zapping to another channel on the Main dispatcher, not just racing
coroutine cancellation timing. `cast/StaleChannelGuard.isCurrent(streamUrl, activeStreamUrl)` is
checked before any of the three places such a continuation could act on a channel that's no longer
current (`fallBackToProxyIfStillDirect`, `onRouteBlocked`, and the diagnostic-result handler
itself) - without it, a late-arriving continuation for an abandoned channel could load the WRONG
(already zapped-away-from) channel onto the receiver, or attribute a codec incompatibility banner to
whatever channel happens to be on screen instead of the one it was actually diagnosed for.

## Watchdog

A stream that's geo-restricted or VPN-only often doesn't error out on the receiver - it just
buffers forever. So after a direct load, if the receiver isn't `PLAYING` within **4 seconds**, the
app falls back to the proxy (`CastSessionRepository.loadDirectWithWatchdog`). This is raced against
`data/cast/TsFirstSegmentDiagnostic` probing the stream for its actual declared video/audio codecs
(`cast/TsProgramInfoParser.kt`, reading PAT/PMT).

The stream URL can point at either an HLS playlist or a raw MPEG-TS origin - IPTV origins routinely
use tokenized/extensionless URLs that give no hint which, and feeding raw TS bytes into a text
playlist parser just silently finds no segments. The diagnostic classifies the initial probe first
(`data/cast/TsSourceClassifier.kt`, reusing `proxy/PlaylistDetector.kt` and `proxy/MpegTsSniffer.kt`
so it agrees with the proxy's own routing) before deciding how to read codecs: HLS fetches its
first segment separately; raw TS is sniffed directly, no second request.

### Routing table

`cast/CastCompatibilityPolicy.kt` turns the probed codecs into a verdict, and
`cast/CastDeliveryStrategy.onDiagnosticResult(verdict, sourceKind)` turns *that* into a routing
decision the moment the diagnostic resolves - even mid-watchdog-window, not just at the 4s mark:

| Verdict | Source kind | Action |
|---|---|---|
| `IncompatibleVideo` (MPEG-2 only) | any | **Blocked** - never proxy (remuxing the container never fixes a codec problem); report the codec to the UI and record the (stream, receiver) pair as incompatible immediately, not on a later receiver-side failure. This is the *only* verdict that blocks an attempt. |
| `Compatible` / `LikelyCompatible` | raw TS | **ProxyImmediately** - skip the direct attempt outright. A receiver never plays a bare MPEG-TS URL directly (it needs HLS/DASH wrapping), so trying direct first is a guaranteed 4s wait for nothing. |
| `Compatible` / `LikelyCompatible` | HLS | **NoAction** - unchanged: direct, then watchdog, then proxy (rewrite, not remux - nothing to remux, it's already HLS). |
| `Unknown` (PAT/PMT not found in the probe window) | any | **NoAction** - unchanged: direct, then watchdog, then proxy. If that proxy attempt turns out to be raw TS, `proxy/RawTsRemuxActivation.kt` still remuxes it (an Unknown verdict isn't a confirmed problem, and a raw-TS passthrough is never playable either way - only a confirmed `IncompatibleVideo` verdict skips remux there). |

`LikelyCompatible` exists because only MPEG-2 video is a *confirmed* incompatibility - HEVC video
and MP2/AC-3/E-AC-3-only audio are a coin flip that depends on the actual receiver hardware (real
receivers routinely play them despite Chromecast's Default Receiver not officially guaranteeing
it), so neither may ever block or reroute an attempt. `LikelyCompatible(audioHint, videoHint)`
carries whichever of the two is iffy purely so a *later* failure message can name a likely cause -
it is otherwise routed identically to `Compatible` in the table above.

A blocked verdict sets `CastPlaybackState.codecIncompatibility` (a `CodecIncompatibility.Video`
carrying the real codec, rendered via `cast/CodecDisplayName.kt` as e.g. "MPEG-2" - see
`cast_incompatible_video_message`) so the player can explain *why* to the user, instead of local
playback silently taking over with no explanation. If the receiver still goes idle/error after all
that (a genuinely unreachable proxy URL, a malformed playlist, an actual HEVC/MP2/AC-3 failure,
etc.), `CastPlaybackState.receiverLoadFailed` covers the generic case: if a `LikelyCompatible` hint
was recorded for this attempt, the message names that likely cause
(`cast_likely_incompatible_video_message`/`cast_likely_incompatible_audio_message`); otherwise it's
the fully generic `cast_receiver_load_failed_message`. Either way the Cast session itself stays
connected and local playback keeps going, so the user can pick a different channel without
reconnecting to the TV.

Every routing decision also logs one self-contained line - `AppLog.d("CastSessionRepository")`:
`cast route: verdict=... source=... action=... video=... audio=...` - deliberately never the
stream URL, so a field logcat alone is enough to diagnose why a specific channel didn't cast.

## Recovery

A receiver going idle mid-playback (`IDLE` with `ERROR`, or `FINISHED` - which for this app's
exclusively-live channels always means an unexpected drop, never legitimate end of content) is
often just a momentary hiccup rather than a real failure - a brief blip on the TV's own Wi-Fi, an
origin server hiccup. `cast/CastRecoveryPolicy.kt` (pure) decides whether to retry: up to 3 reload
attempts with 2s/4s/8s backoff, `Ignore` for a self-initiated IDLE (see "Load generations..." above)
or a `CANCELLED`/`INTERRUPTED`/`NONE` idle reason, and an immediate `GiveUp` for a confirmed
`IncompatibleVideo` verdict (reloading can never fix a codec problem) or once the attempt budget is
spent. `CastSessionRepository.tryRecover` intercepts a recoverable IDLE *before* it ever reaches
`CastReceiverStatusReducer.reduce`'s normal give-up branch: `Reload` schedules a delayed reload of
the exact same channel via the exact same delivery mode (direct or proxy - the proxy path reuses the
running server per "Proxy starts once per cast session" above, so a reload never rebinds anything),
setting `CastPlaybackState.isRecovering` so the UI shows "Recovering the stream…"
(`cast_recovering_message`) instead of silently bouncing to local playback and back; `Ignore` and
`GiveUp` both fall through to the reducer's existing behavior unchanged. The attempt counter resets
early - before evaluating a new failure - once the stream has held a stable, continuous `PLAYING`
for `CastRecoveryPolicy.STABLE_PLAYING_RESET_MILLIS` (60s), so a channel that's been fine for hours
doesn't inherit a nearly-exhausted budget from a brief flaky patch hours earlier. Every recovery
decision logs one line: `cast status: state=IDLE idleReason=<...> mode=<...> playedMs=<...>
action=<...>`.

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
