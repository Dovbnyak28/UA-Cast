# Changelog

Versions are marked in two places, which must move together: the local defaults in
`app/build.gradle.kts` and `UACAST_VERSION_NAME` in `.github/workflows/android-ci.yml`. CI appends
its run number to both (see `docs/RELEASING.md`), so a CI artifact reads `0.3.0.<run>` with a
`versionCode` of the run number - the values below are what a local build produces.

## 0.3.0 - unreleased

`versionCode` 3. A minor bump rather than a patch: DLNA casting became a real feature in this
version, and the local player's behaviour during a remote cast changed.

### Added

- **DLNA/UPnP casting** to any AVTransport renderer on the LAN - typically an older Smart TV with
  no Chromecast support. Own SSDP discovery and SOAP client, no new dependencies, reusing the same
  local HLS proxy Chromecast already goes through. See `docs/DLNA.md` for what this MVP does and
  does not do.
- **Channel switching while casting to DLNA.** The renderer is re-pointed at the new channel
  instead of being left on the old one.

### Fixed - security

- **Credentials embedded in a url no longer leak into a diagnostics report.** `LogSanitizer`
  redacted through `URI.getAuthority`, which includes userinfo, so
  `http://user:pass@host/path` came out with `user:pass` intact - in the one component whose
  purpose is keeping credentials out of a report the user shares. It now keeps only scheme, host
  and port, and redacts a url whole when the host cannot be parsed.

### Fixed - casting

- **Cast streams breaking up into constant rebuffering.** Two separate causes: the remux window was
  capped in bytes only, so a high-bitrate channel got a window too short in *seconds* and segments
  rolled off before the receiver fetched them (now 48MB with a six-segment floor); and the receiver
  started three segments from the live edge with no slack for a VPN or a congested link (the
  playlist now carries `#EXT-X-START` at half the window). Reported from the field; not reproducible
  without the reporter's own channels and receiver.
- **The proxy could hand the receiver the VPN's address instead of the LAN's.** A VPN inherits the
  transports of the network it runs over, so a VPN tunnelled over Wi-Fi reports `TRANSPORT_WIFI`
  exactly like real Wi-Fi, and `allNetworks` has no defined order - whichever came first won. The
  receiver then had a url on a private range it cannot route to, accepted the load, fetched nothing,
  and went idle/error, which is indistinguishable on the sender from a codec problem. Matches the
  field reports of casting being unusable specifically while a VPN is connected.
- **The DLNA proxy read live streams through a 4-second-timeout HTTP client** meant for fetching one
  small device-description XML, so any four seconds without a byte read as a dropped connection and
  sent the reader into a backoff reconnect. It now gets the same 10s/15s budget as the Chromecast
  path.
- **Switching channels during a DLNA cast started local playback on the phone**, taking a second
  connection to an origin that allows one per account and starving the proxy feeding the TV.
- **Leaving the app during a DLNA cast could kill the TV's stream.** DLNA held only a wake lock,
  which keeps the CPU awake but does not stop the OS reclaiming a backgrounded process. It now uses
  the same foreground service the Chromecast path does.
- A channel switch during a DLNA cast rebound the proxy's socket out from under a renderer that
  could still be fetching the previous url.
- A `stop()` racing a reconnect could close the wrong upstream response, leaving the reader blocked
  until its socket timeout with the connection still held.

### Fixed - crashes

- **Loading a large EPG could kill the app with an OutOfMemoryError.** Caught in a field logcat:
  the heap sat pinned at the 255MB/256MB growth limit for ninety seconds - every allocation
  process-wide triggering a blocking GC, the main thread skipping up to 797 frames at a time - and
  then died inside the XMLTV parse. The cause was `<desc>`: XMLTV's programme description is by
  far the largest field in a real feed, it was retained for up to 250,000 programmes, and nothing
  in the app has ever read it (the guide sheet shows a title and a time). Measured on a
  500-channel, 200k-programme feed, dropping it takes the parsed EPG from **109MB to 27MB
  retained** and the parse itself from 569ms to 376ms. See `EpgProgramme` for what to do
  differently if descriptions are ever shown.
- The per-name text cap came down from 16KB to 512 characters alongside it. 16KB was sized for
  descriptions; applied to 250,000 titles it left the worst case in the gigabytes. The manifest declared
  `androidx.media3.session.MediaButtonReceiver`, which exists to wake a `MediaSessionService` and
  throws when there is none - and this app deliberately has none, since live TV is not expected to
  keep playing after the player closes. On API 31+ media3 picks a declared receiver as the media
  button target, so the first press would have gone straight into that throw. Removing the
  declaration loses nothing: media3 registers its own in-process receiver when neither a receiver
  nor a service is declared.

- **Importing a hand-edited or corrupt backup crashed the app.** `BackupCodec.decode` guarded only
  the initial JSON parse, so a stray non-object element in the `sources` or `favorites` array threw
  straight out of the import coroutine - contradicting the codec's own documented promise to return
  null for anything unusable. A bad row is now skipped and the valid entries around it still import.
- **An unreadable favorites, locked-channels, group-visibility, playlist or EPG file crashed the app
  on startup**, every startup: each store caught only `FileNotFoundException`, letting any other I/O
  error escape into a `launch` where nothing catches it.
- **A failed write crashed the app** on the same six stores - starring a channel with a full disk was
  enough. Writes now report failure instead of throwing; the in-memory state the caller already
  updated stays correct, so a lost write costs that one change at next launch.

### Fixed - data

- **XMLTV feeds could record the literal string `"null"` as a channel's display name** when a
  `<title>`/`<desc>` was nested inside `<display-name>`.
- **Icon cache writes could publish a truncated file.** Concurrent fetches of one logo url - routine,
  since prefetch runs six at a time and HD/SD/FHD variants share a logo - wrote to the same
  url-derived temp file. Temp files are now unique, and a failed write reports failure instead of
  returning a path to a file that does not exist.
- **Leftover `.tmp` files in the icon cache were never deleted and never counted** against the
  256MB budget, because the trim pass filtered them out of its own listing.
- Switching playlist source twice quickly left two loads racing, and whichever finished last won -
  not whichever the user picked last.
- A malformed `\u` escape in a cached JSON file reported a bare `NumberFormatException` instead of
  the parser's own error with a position in it.

### Diagnostics

Prompted by a field logcat of a failing cast that turned out to be unreadable:

- The local LAN address handed to the receiver is logged. Without it, a wrong-interface failure
  looks exactly like a codec or content failure.
- An ordinary (non-remux) HLS cast now logs its rewritten playlist. Only the remux path logged
  anything before, so the common case left no trace of whether the receiver ever fetched.
- A request for a resource this session never registered is logged instead of a silent 404.
- Proxy connection errors say which phase they happened in and, where known, the path. A peer that
  connects and hangs up without sending a request - a routine reachability probe - is no longer
  reported as a warning.

### Performance

Measured before and after; ratios hold on device even though the absolute numbers are from a
desktop JVM.

- **Channel search: 5076µs → 660µs** per query over 10k channels, and no longer allocates. It also
  moved off the composition thread, so a search no longer blocks the frame that composes it.
- **URL fingerprinting: 3008µs → 750µs** per 5000 digests. `MessageDigest.getInstance()` is a JCA
  provider lookup and was ~75% of the cost for inputs this short.
- **EPG memory:** channel ids are pooled, collapsing up to 250k duplicate strings into one per
  distinct channel. Dropping the unread `<desc>` (above) cut the rest by a further 75%.
- **XMLTV timestamps: 260µs → 49µs** per 500k parses, producing bit-identical results. This runs
  twice per programme, and each call was allocating a `GregorianCalendar` and looking up the UTC
  zone through `TimeZone.getTimeZone`, which is internally synchronized - the same field logcat
  showed the EPG worker holding that monitor for 1.2 seconds at a stretch with other threads
  queued behind it. It is now plain arithmetic over the string's indices, with no allocation and
  no lock. Six throwaway substrings per timestamp went with it, about three million per feed.
- **Playlist parsing** no longer builds the whole file a second time in memory as a list of lines.
- Channel-row initials no longer run a regex and build four intermediate lists per recomposition.
- Flattening a loaded playlist moved off the main thread.
- Entering or setting the parental-control PIN no longer freezes the frame it was submitted on:
  120,000 PBKDF2 rounds (~26ms on a desktop JVM, several times that on a low-end phone) moved off
  the main thread.

### Changed

- A lone `\r` now separates lines in an M3U playlist (classic-Mac endings); previously only a
  trailing one was stripped from `\n`-delimited lines.
- Channel-search case folding is per character rather than `String.lowercase`'s context-sensitive
  mapping. These differ only for Greek final sigma and dotted-I forms.

## 0.2.0

The last version before this changelog existed; see the git history for what it contained.
