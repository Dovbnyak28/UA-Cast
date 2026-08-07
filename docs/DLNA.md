# DLNA/UPnP casting

A second, independent cast target alongside Chromecast: `dlna/` lets the app push the current
channel to any UPnP AVTransport renderer on the LAN - typically an older Smart TV (Samsung, LG,
etc.) with no Chromecast support. No Chromecast SDK involved, no new third-party dependencies -
own minimal SSDP discovery and SOAP client.

## Why it reuses the Cast proxy

`dlna/DlnaSessionRepository` starts and owns its own `data/cast/ProxyServer` instance (see
`docs/PROXY_RULES.md`) exactly the way `cast/CastSessionRepository.startProxyAndLoad` does: start
the proxy, register the channel's playlist url, hand the resulting `http://phone-ip:port/hls/...`
url to the renderer instead of the origin url. This is deliberate, not incidental - the same
geo-restriction/TLS-quirk/header problems that make Chromecast need a local relay apply just as
much to a DLNA renderer, and it keeps exactly one proxy implementation in the app instead of two.

Two consequences of "same proxy" that are easy to get wrong, and were:

- **The proxy gets its own OkHttp client** (10s connect / 15s read, matching the Chromecast path),
  never the discovery client's few seconds. The discovery timeouts are sized for one small
  device-description XML; the proxy's client is what reads an *endless live stream*, and OkHttp
  applies the read timeout to every `read()` on the body - so a few seconds without a byte, routine
  through a VPN, would read as a dropped connection and send the remux reader into a backoff
  reconnect plus a discontinuity.
- **`ProxyServer.ensureStarted`, not `start`.** One session token is generated per DLNA session and
  reused, so a channel switch reuses the running socket and port instead of rebinding a fresh one
  out from under a renderer that may still be fetching the previous url. `start` is only reached
  through `ensureStarted`'s own new-session path.

## Discovery

`dlna/SsdpDiscovery` sends SSDP M-SEARCH multicast datagrams to `239.255.255.250:1900` and collects
`LOCATION` headers from replies for about 4 seconds. SSDP replies come back as plain unicast UDP to the
sender's ephemeral port, so a normal `DatagramSocket` is enough - no `MulticastSocket` needed. A
`WifiManager.MulticastLock` is still held for the duration of the search only, acquired right
before sending the M-SEARCH and released the moment the search window ends (success or failure),
the same acquire/release discipline `data/cast/CastWakeLocks` uses for the proxy session's
power/Wi-Fi locks.

What is sent is `dlna/SsdpSearchRequests`, and both details there matter more than they look:

- **Three search targets** (`AVTransport:1`, `MediaRenderer:1`, `ssdp:all`), not just the service
  one. A renderer is supposed to answer an `ST` naming its own service and many do, but plenty of
  real TVs only answer at the device level or ignore anything but the wildcard. Asking one narrow
  question makes the device list depend on a quirk of the TV's firmware. The extra replies are free:
  a description with no AVTransport service is dropped by `DeviceDescriptionParser` anyway, which is
  already how a non-renderer gets rejected.
- **Each target sent twice**, spaced 100ms apart. Multicast UDP has no retransmission, and over
  Wi-Fi it goes at the lowest basic rate and is the first thing dropped under load. One datagram per
  search made "the TV was busy for 40ms" indistinguishable from "there is no TV". The spacing is
  there because a back-to-back burst is itself something the Wi-Fi driver may drop.

The listen window is measured from the *first* datagram sent, not the last: devices spread replies
randomly over `MX` seconds, so the window must cover the send sweep plus a full `MX` after it. The
`ssdp:all` target means a busy LAN can return many locations, so the list is capped before the
description fetches - renderers show up long before the cap.

Discovery logs its counts at each stage (datagrams sent, distinct locations, usable renderers),
including - especially - when the result is empty. An empty device sheet is the symptom users
report, and the counts are what separate "nothing answered the multicast" from "several devices
answered but none of them can receive video".

Each collected `LOCATION` is fetched (body size capped via `core/io/BoundedByteReader`) and parsed
by `dlna/DeviceDescriptionParser`, a hardened SAX parser (external entities disabled, same
discipline as `epg/XmlTvParser`) that pulls out `friendlyName` and the `controlURL` of the first
`AVTransport` service. `controlURL` is very often a relative path in real device descriptions, so
it is resolved against the device's own `LOCATION` url before being stored.

## Casting

`dlna/AvTransportClient` POSTs three SOAP actions to the renderer's control url:
`SetAVTransportURI` (with a minimal DIDL-Lite metadata block - title only, no elaborate library
metadata since this is live TV, not a movie), `Play`, and `Stop`. Envelope/DIDL-Lite string
building is pure (`dlna/AvTransportSoapBuilder`); the HTTP POST is the only impure part.

Four details here are compatibility, not style, and each was a real failure:

- **Every connect sends `Stop` first** - a channel switch and a first connect alike. The AVTransport
  state table only guarantees `SetAVTransportURI` from STOPPED/NO_MEDIA_PRESENT; from anything else
  it is renderer-specific. A Samsung UE40KU6000 accepted the new url but then refused `Play` with
  `701 Transition not available` four times in a row on a channel switch, and five times on a first
  connect - three seconds of a user watching nothing. The first connect was exempt at first, on the
  reasoning that a fresh connect finds an idle set. It does not; see *What the renderer's state
  actually does* below. Stopping first turns a renderer-specific case into the one every renderer
  implements.
- **`Play` retries while - and only while - the renderer answers 701.** That is the one refusal that
  resolves on its own; `716 Resource not found` and everything else fail immediately, since retrying
  them only delays the fallback. This is the safety net under the `Stop`, not the primary fix.
- **`protocolInfo` keeps a wildcard MIME** (`http-get:*:*:...`). One proxy url serves a rewritten
  HLS playlist, remuxed `video/MP2T`, or an origin passthrough depending on the channel, and which
  is not known when the metadata is built. It used to name the HLS type specifically - a claim the
  proxy could not keep, and one most non-Samsung sets reject outright since they do not do HLS over
  DLNA. Renderers play by the response's `Content-Type`; protocolInfo is a pre-flight filter, so the
  safe value is the one that filters nothing. The DLNA attributes (`DLNA.ORG_OP=00`, streaming-mode
  `DLNA.ORG_FLAGS`) and the `videoBroadcast` class say "live, not seekable", so a renderer does not
  treat the channel as a file and stall on a range request.
- **`SetAVTransportURI` gets a much longer timeout than `Play`/`Stop`.** Renderers commonly fetch
  the url inside the action to validate it - the Samsung answers `716` for a dead url, which it can
  only know by having tried - so that action's reply is gated on the proxy's first round trip to the
  origin. On the shared few-second budget a slow origin surfaced as a socket timeout and a failed
  connect on a TV that was working fine.

A failed channel switch no longer tears the session down. Only a first connect does. On a re-point
the renderer is still playing the previous channel through this proxy, and stopping it was itself
what put an error on the TV - the failure is usually a refused action, not a dead stream.

A renderer that refuses an action answers HTTP 500 with a UPnP fault rather than throwing, so until
`dlna/UpnpFault` existed a rejected action produced no log line at all and looked exactly like one
that worked. The error code is the whole diagnosis; it is now logged.

`ui/dlna/DlnaDeviceSheet` is the "Other devices (DLNA)" bottom sheet opened from a small icon next
to the player's other overlay controls: it runs discovery once per appearance, lists whatever it
finds, and a tap on a device calls `DlnaSessionRepository.connect`. A separate "Stop casting"
action calls `DlnaSessionRepository.stop`, which stops the renderer and tears down the proxy
session and its locks.

While connected, the session is held up by `cast/CastProxyService` - the same foreground service the
Chromecast path uses, started with `CastProxyTarget.DLNA` so its notification's Stop action ends the
DLNA session rather than a Chromecast one. This replaced a bare `CastWakeLocks` acquire: a
`PARTIAL_WAKE_LOCK` keeps the CPU awake but does nothing to stop the OS reclaiming a backgrounded
process, and the proxy only matters while it is *serving* - so leaving the app killed the TV's stream
on exactly the aggressive OEM builds that service was written for. The service owns the wake/wifi
locks for its own lifetime; `DlnaSessionRepository` holds none of its own.

`stop()` tears the proxy and locks down *synchronously* and only defers the SOAP `Stop`: with the
teardown behind the coroutine that first waits out a renderer's timeout, a user who stopped and
immediately picked another device had that pending teardown kill the proxy the new session had just
bound. Neither call blocks - `ProxyServer.stop` closes sockets and signals its reader threads
without joining them.

## What the renderer's state actually does

Measured on the Samsung UE40KU6000 on 2026-08-04 by calling `GetTransportInfo` and `GetMediaInfo`
against the set's own control url from outside the app (`curl` on the phone, since the renderer and
the development machine sit on different subnets). The app itself still polls neither - see
*Explicitly out of scope* below; this was a diagnostic, not a feature.

Three findings, each of which contradicts something the code around `Stop` assumed:

- **A renderer does not stay PLAYING when its sender dies.** Killing the app takes the proxy with
  it, and about eight seconds later the set reports `STOPPED` with `ERROR_OCCURRED` - it notices the
  stream is gone and gives up on its own. So "it is still PLAYING whatever the previous app left on
  it" is *not* the mechanism behind the refusals, however plausible it sounded.
- **The state that actually bites is `PAUSED_PLAYBACK`, and the app's own clean stop is what
  produces it.** After `DlnaSessionRepository.stop`, the Samsung sat in `PAUSED_PLAYBACK`
  indefinitely rather than `STOPPED`. Every first connect that follows a normal "Stop casting"
  therefore starts from a state the AVTransport table does not guarantee `SetAVTransportURI` from -
  which is the ordinary path through the app, not an edge case.
- **"We sent `Stop`" does not mean "it is stopped".** The set answered `Stop` with HTTP 200 and
  still reported `PAUSED_PLAYBACK`; a second `Stop` was needed before it read `STOPPED`. This is the
  real argument for sending it unconditionally *and* ignoring its result: the action's return value
  says nothing about the state that follows it, so there is nothing to branch on.

**Verified.** Four first connects - from a cold-booted idle set, from `STOPPED`/`ERROR_OCCURRED`,
and from `PAUSED_PLAYBACK` - produced zero SOAP refusals across 4,779 lines of logcat, against five
`701`s in a row on the same hardware before the fix. `AvTransportClient` logs only refusals and
failures, so the absence of the tag is the measurement.

Getting a renderer into `PAUSED_PLAYBACK` with no DLNA session live takes a second sender to keep
the proxy alive: cast to Chromecast, connect DLNA, stop the DLNA session, then connect again. Worth
knowing before anyone tries to reproduce this from a cold TV, where every state is one the table
already guarantees and the bug cannot appear.

Unrelated to the state machine but found the same way: **this set cannot fetch HTTPS.** An external
`https://` url is refused outright with `716 Resource not found`. Nothing in the app depends on
that - every url it hands out is the local plain-HTTP proxy - but it does mean the proxy is not
merely a convenience for this renderer.

## Channel switching

`PlayerViewModel.switchToIndexImmediate` calls `DlnaSessionRepository.setActiveChannel`
unconditionally - the DLNA counterpart of `CastSessionRepository.setActiveChannel`, and a no-op
unless a renderer is connected. It re-runs `connect` for the already-connected device, which
re-registers the new channel with the (still running, same port) proxy and sends a fresh
`SetAVTransportURI` + `Play`.

This is not optional bookkeeping. The local player stands down for *any* remote target -
`LocalPlaybackPolicy.shouldPrepareLocally(isRemoteCasting)`, note `isRemoteCasting`, not
`isCasting`, or the phone opens a second connection to an origin that allows one per account and
starves the proxy feeding the TV. So without `setActiveChannel` a channel switch during a DLNA cast
changed nothing anywhere: the TV kept the old channel and the phone played nothing.

## Explicitly out of scope for this MVP

- **No renderer position/state tracking.** The app never polls `GetPositionInfo` or
  `GetTransportInfo`. There is no seek-on-the-TV and no progress bar synced back from the
  renderer - once `Play` succeeds, the app simply shows "connected" until the user hits stop.
- **No volume control.** `SetVolume`/`GetVolume` are not implemented; volume is whatever the TV's
  own remote sets it to.
- **Codec compatibility is not gated.** `cast/CastCompatibilityPolicy.kt` producing Chromecast
  codec verdicts is intentionally *not* consulted here. Real DLNA/UPnP TVs are generally far more
  codec-permissive than a Chromecast Default Receiver (MPEG-2, MP2, AC-3, HEVC are usually fine),
  so gating the DLNA path on Chromecast's verdicts would reject streams a real TV can play just
  fine. The DLNA path always sends the stream and lets the renderer decide.
- **DLNA and Chromecast are mutually exclusive.** `DlnaSessionRepository` and
  `CastSessionRepository` are independent singletons with no shared state (each owns its own
  `ProxyServer` instance); there is no handling for both being "connected" at once, and no attempt
  to hand a session off between them.
