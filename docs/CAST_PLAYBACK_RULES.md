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

There are two of them, and they answer different questions. Keeping them straight matters: they
were once the same 4-second constant, and that was a bug (below).

**The direct-mode watchdog** (`CastSessionRepository.loadDirectWithWatchdog`) decides the
direct→proxy *mode switch*. A stream that's geo-restricted or VPN-only often doesn't error out on
the receiver - it just buffers forever. So after a direct load, if the receiver isn't `PLAYING`
within **4 seconds**, the app falls back to the proxy. This is raced against
`data/cast/TsFirstSegmentDiagnostic` probing the stream for its actual declared video/audio codecs
(`cast/TsProgramInfoParser.kt`, reading PAT/PMT). Flat 4s is right here: nothing travels through
the phone in direct mode, and firing costs only a mode switch.

**The stall watchdog** (`CastSessionRepository.scheduleStallWatchdog`, policy in
`cast/CastStallWatchdogPolicy.kt`) covers everything after that - proxy loads and recovery reloads -
and asks "is this load stuck?", answering it from **bytes delivered, not elapsed time**. It ticks
every 4s and fires only on a tick where the proxy served the receiver *nothing at all*, up to a 30s
ceiling.

It used to be a flat 4s too, and that could not work. On the proxy path every byte goes
origin → phone → receiver, and one segment of an HD channel measured 3.6-6.5MB taking ~2-3s to move;
a receiver buffering two of them cannot report `PLAYING` inside 4s. A device capture of one channel
showed the watchdog firing **four times in 30 seconds** while the proxy was delivering a complete
3.6MB segment roughly every 2 seconds without a gap - and each firing forced a reload that aborted
the in-flight transfers (`Passthrough served: 200, 212534B` of a 3.6MB segment, then
`SocketException`), so every attempt started further behind than the last. The channel never played;
it just escalated through the `CastRecoveryPolicy` backoff to 30s. After the change, the same
channel on the same receiver plays with zero firings.

The policy is deliberately mode-agnostic rather than taking a "is this proxy mode?" flag: in direct
mode the proxy serves nothing, so the byte delta is always zero and it fires on the first tick,
exactly like the flat timeout it replaced. There is no way for the two paths to drift apart.

### Remembering that direct never works

`data/cast/IncompatibilityMemoryStore` is read by `CastDeliveryStrategy.initialMode` to skip the
direct attempt entirely for a (stream, receiver) pair. Despite the name it is not a codec claim -
it is only ever asked "should this pair go straight to the proxy?", and two rules write it:

- `IncompatibilityRecordingPolicy` - a confirmed hard-incompatible codec.
- `DirectRouteMemoryPolicy` - the direct attempt never played **and the proxy then did**.

The second was missing entirely, which meant the common case - the direct watchdog timing out - was
never remembered, and every cast of every channel re-paid its 4 seconds of dead air. The bar is
deliberately the *pair* of facts, not just "direct failed": a direct attempt can fail because the
origin blinked, and recording that would push a channel through the phone for 30 days over one bad
moment. Proxy playback succeeding on the same stream moments later rules that out.

### Artwork

The load request carries the channel's `tvg-logo` as a `WebImage`, so the receiver shows the channel
logo rather than a bare title on black. Channels whose icon comes from the EPG or the CDN fallback
instead (see `icons/IconResolver.candidates`, which is the full precedence the app's own UI uses)
send no artwork: that chain needs the EPG index, which lives in `AppViewModel`, and the cast load is
built from `PlayerViewModel`. Closing that gap means plumbing an `epgIconUrlFor`-style lookup down
to the cast layer. `cast load: artwork=<bool>` records which case a channel hit, without ever
logging the url.

### Diagnostic warm-up and cache

Probing a stream is a real HTTP fetch, and casting or re-casting the same channel shouldn't pay for
it every time. `data/cast/DiagnosticResultCache.kt` (an LRU cache of 32 entries keyed by stream URL,
governed by `cast/DiagnosticCachePolicy.kt`) remembers the verdict; `loadDirectWithWatchdog` checks
it first and, on a hit, skips the HTTP probe entirely. A `Compatible`/`LikelyCompatible`/
`IncompatibleVideo` entry is trusted for the rest of the process's lifetime, but an `Unknown` one
(PAT/PMT not found in the probe window) only for 10 minutes - it might just have caught the origin
at a bad moment (mid-ad-break, a transient encoder hiccup). A later probe result for the same URL is
merged in, never *replacing* a more decisive existing verdict with a less decisive one - a confirmed
`IncompatibleVideo` can't be silently downgraded back to `Compatible` by a flaky re-probe.

`CastSessionRepository.setActiveChannel` also warms the cache proactively while the player is just
browsing locally, not casting: 1.5 seconds after landing on a channel (zapping past it before then
cancels the warm-up outright), it's probed in the background, so a channel the user has actually
settled on already has an answer cached by the time they tap Cast. This warm-up never touches
casting UI state on its own - if it happens to still be in flight when casting actually starts,
`startPlayback` cancels it immediately in favor of the watchdog's own immediate probe, which is
never gated by the debounce.

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

Recording only happens where `cast/IncompatibilityRecordingPolicy.kt` (pure) says the failure is
genuine, not transient - either a confirmed `IncompatibleVideo` verdict, or a channel that never
reached `PLAYING` at all across the whole casting episode including every recovery reload (see
"Recovery" above). A channel that played, however briefly, before eventually giving up is treated
as a one-off blip, not evidence the pair doesn't work - recording it anyway would skip a channel
that's actually fine straight to the proxy for the next 30 days over a single bad moment.
`IncompatibilityMemoryStore` bumped its on-disk schema version for this change and wipes every
existing entry once on first run after the update, since an entry written under the old
"record every failure" rule can't be told apart from one that would still qualify under the new one.

## Pending channel switch

Switching channels while casting doesn't touch the local player (it's paused/idle for the whole
cast session) - it immediately re-loads the new channel on the receiver, *and* queues the index as
"pending". When the session eventually disconnects, the local player catches up to whatever
channel was last requested during the cast session, not whatever it happened to be paused on when
casting started.
