# Cast playback rules

## Direct-first

Every cast attempt starts direct: the receiver is handed the origin stream URL, no relay involved
- it's faster and puts no load on the phone. A (stream, receiver) pair already proven to require
the proxy skips direct. A cached compatible raw-TS observation also goes straight to proxy.
A cached hard-incompatible codec is reported after replacing receiver media, preserving the
existing handoff: skipping `load()` would leave the previous channel playing on the TV.

Before a direct `load()`, any previous Chromecast proxy is stopped. A remux or flattened producer
can otherwise keep its origin open even when the receiver no longer fetches the proxy, blocking
the new direct stream on a one-slot provider. Cleanup removes only Chromecast service ownership,
not the Cast SDK session or another active DLNA owner. Later fallback can restart the proxy.
Proxy → proxy retains the existing port/resource handoff when host/token are unchanged.

## State reduction and side effects

`CastLoadResultReducer` and `CastReceiverStatusReducer` handle SDK load outcomes and receiver
status events. Separate reducers handle proxy preparation failure and codec verdicts.
`CastSessionRepository` orchestrates loads/timers, initializes pending-media state, and applies
their state/effects; the reducer classes themselves do not call the SDK or open network resources.

- **Load result reducer** - success acknowledges the load (`loadPhase=LOADED`); it is not proof
  of `PLAYING`. `loadOnReceiver()` pauses/stops local playback through a side effect *before*
  issuing the SDK load, to free its upstream. A terminal load failure records incompatibility
  and resumes local playback. The repository handles an eligible Direct failure by trying Proxy
  before applying that terminal failure reducer.
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

Each receiver load also resets media status to neutral `IDLE`/`NONE` while `loadPhase=LOADING`.
This is pending media, not a terminal receiver event. The previous channel's stored `PLAYING`
must not settle either watchdog or describe the replacement as playing; a matching accepted
receiver callback supplies the new status. SDK load acknowledgement alone does not prove playback.

The receiver's own status stream (`RemoteMediaClient.Callback.onStatusUpdated`) has the same
problem from a different angle: issuing a new load naturally interrupts whatever the receiver was
doing, and the SDK reports that as an ordinary `IDLE` (`CANCELLED`/`INTERRUPTED`/`NONE`), not an
error. `CastReceiverStatusReducer.reduce` takes a `selfInitiated` flag - true only for the first
status update after this app itself issued a load - and ignores that specific IDLE shape entirely
when it's set; an `IDLE` that's actually `ERROR` or `FINISHED` still goes through normal handling
even if self-initiated, since a real error is still an error.

## Stale channel guard

Deferred watchdog callbacks check the load generation and `StaleChannelGuard` before acting.
Source/codec observations originate on proxy workers and are dispatched to the repository's Main
scope. After dispatch, the repository rechecks the Cast attempt, active resource ID, and exact
proxy producer lease, not just URL equality. Thus A → B → A, same-token stop/start, and changed
access headers cannot make an obsolete observation update the current channel's UI or cache.

## Watchdog

There are two of them, and they answer different questions. Keeping them straight matters: they
were once the same 4-second constant, and that was a bug (below).

**The direct-mode watchdog** (`CastSessionRepository.loadDirectWithWatchdog`) decides the
direct→proxy *mode switch*. A stream that's geo-restricted or VPN-only often doesn't error out on
the receiver - it just buffers forever. So after a direct load, if the receiver isn't `PLAYING`
within **4 seconds**, the app falls back to the proxy. It does not launch a diagnostic GET alongside
the receiver: that second connection can displace the stream on a single-slot provider account.
Uncached raw TS therefore waits for the ordinary watchdog rather than receiving an early diagnostic
fallback. A previously cached source/verdict can select proxy before loading the receiver.

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

### In-band diagnostics and cache

`CastDiagnosticCoordinator` performs no network I/O. It caches observations from the current proxy
producer in `DiagnosticResultCache` (32-entry LRU, governed by `DiagnosticCachePolicy`). Keys contain
the URL and sanitized access headers, matching proxy resource identity. Display title/index/artwork
are not part of the key. `startPlayback` consults this cache before issuing a receiver load.
Decisive verdicts retain their existing process-lifetime policy; Unknown expires after 10 minutes.
Less decisive observations do not replace a stronger verdict for the same request identity.

`CastSessionRepository.setActiveChannel` only remembers a channel when there is no Cast session.
There is **no speculative warm-up while browsing/playing locally** and no separate diagnostic
fetch during direct Cast playback. Both could displace the real consumer of a one-connection stream.

`ProxyRouteSelector` publishes the source/verdict it already computed while routing the response.
There is no extra fetch, peek, or PAT/PMT parse. Successful root HLS yields Hls/Unknown; root raw TS
uses the existing bounded 128-KiB remux probe. Wrapper playlists can update their root observation
from the already-required inner response. Raw probing remains disabled when remux is disabled.
Nested HLS segments/sub-playlists do not produce root codec verdicts or additional probe reads.

Only successful responses yield observations; 403/404/500 bodies do not become codec metadata.
An observer failure is logged without interrupting response serving. Observations retain a producer
lease but no response, socket, Activity, or UI reference. HLS codecs are no longer fetched by a
standalone probe: receiver errors remain the fallback signal, and no arbitrary variant can block
the channel. `TsFirstSegmentDiagnostic` remains a standalone utility, not wired to playback.

`CastDirectNetworkOwnershipTest` reproduces the previous receiver/probe conflict with real HTTP
sockets and a shadowed Cast SDK. It now checks both one load and 30 rapid loads with late SDK results.
Proxy lease and Cast attempt tests cover queued observations after switch, A → B → A, and stop/start.
These host-side tests do not establish acceptance on a physical receiver or Hisense VIDAA.

### Routing table

`core/cast/CastCompatibilityPolicy.kt` turns the probed codecs into a verdict, and
`cast/CastDeliveryStrategy.onDiagnosticResult(verdict, sourceKind)` turns *that* into a routing
decision from cached metadata before load, or from a current in-band observation after proxy routing:

| Verdict | Source kind | Action |
|---|---|---|
| `IncompatibleVideo` (MPEG-2 only) | any | **Blocked** - report the codec and suppress pointless recovery. A cached verdict does not skip receiver media replacement. First-time uncached detection happens only after proxy fetch. Remux does not fix codecs. |
| `Compatible` / `LikelyCompatible` | raw TS | **ProxyImmediately** - cached compatible raw TS selects the application's proxy/HLS-wrapping route before direct load. |
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
(`core/cast/IncompatibilityMemoryPolicy.kt`). The next time that exact pair is cast, `CastDeliveryStrategy`
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
