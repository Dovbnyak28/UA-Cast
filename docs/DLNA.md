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

## Discovery

`dlna/SsdpDiscovery` sends a single SSDP M-SEARCH multicast datagram (`ST:
urn:schemas-upnp-org:service:AVTransport:1`) to `239.255.255.250:1900` and collects `LOCATION`
headers from replies for about 3 seconds. SSDP replies come back as plain unicast UDP to the
sender's ephemeral port, so a normal `DatagramSocket` is enough - no `MulticastSocket` needed. A
`WifiManager.MulticastLock` is still held for the duration of the search only, acquired right
before sending the M-SEARCH and released the moment the search window ends (success or failure),
the same acquire/release discipline `data/cast/CastWakeLocks` uses for the proxy session's
power/Wi-Fi locks.

Each collected `LOCATION` is fetched (body size capped via `core/io/BoundedByteReader`) and parsed
by `dlna/DeviceDescriptionParser`, a hardened SAX parser (external entities disabled, same
discipline as `epg/XmlTvParser`) that pulls out `friendlyName` and the `controlURL` of the first
`AVTransport` service. `controlURL` is very often a relative path in real device descriptions, so
it is resolved against the device's own `LOCATION` url before being stored.

## Casting

`dlna/AvTransportClient` POSTs three SOAP actions to the renderer's control url:
`SetAVTransportURI` (with a minimal DIDL-Lite metadata block - title only, no elaborate library
metadata since this is live TV, not a movie), `Play`, and `Stop`. Envelope/DIDL-Lite string
building is pure (`dlna/AvTransportSoapBuilder`); the HTTP POST is the only impure part. Timeouts
are a few seconds - a renderer that doesn't respond promptly fails fast rather than hanging the UI.

`ui/dlna/DlnaDeviceSheet` is the "Other devices (DLNA)" bottom sheet opened from a small icon next
to the player's other overlay controls: it runs discovery once per appearance, lists whatever it
finds, and a tap on a device calls `DlnaSessionRepository.connect`. A separate "Stop casting"
action calls `DlnaSessionRepository.stop`, which stops the renderer and tears down the proxy
session and its locks.

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
