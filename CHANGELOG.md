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
- **An upstream connection could be leaked between fetching it and serving it.** The proxy owns
  the origin response from the moment it is fetched until a serve path takes it on, and the two
  content sniffs in between were guarded individually - but the route-attempt counter that runs
  after them writes to a disk-backed store on the same thread, and an I/O error out of it left the
  connection open with nobody holding a reference. Seen in the field as OkHttp's "connection was
  leaked. Did you forget to close a response body?" against the origin host. The whole window is
  now covered by one guard instead of a per-call one.
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

### Fixed - storage

- **Half a gigabyte of orphaned EPG downloads.** Every path through `EpgDownloader`/`EpgRepository`
  deletes its own temp file, but none of that runs when the *process* is killed mid-download or
  mid-parse - and an XMLTV parse is exactly where this app has been killed before (see the
  OutOfMemoryError entry below). Nothing ever swept the leftovers, and because they live in
  `filesDir` rather than the cache directory, Android never reclaims them either. Found on a real
  device as **13 stranded files totalling ~500MB, out of an app footprint of 523MB**; one force-stop
  during a download was enough to strand another 46MB permanently. A sweep now runs at startup -
  not only before a download, since the common case is that no download happens at all and the EPG
  is restored from its snapshot. Age-gated exactly like the icon cache's equivalent, so it can never
  delete a file another download is still writing into. Measured on the reporting device: 523MB to
  45MB.

### Fixed - data

- **Cyrillic channel names arrived as mojibake from playlists served over HTTP.** The charset in the
  response's `Content-Type` was treated as authoritative, and IPTV panels are wrong about it often
  enough to matter: a body that is really UTF-8 announced as `charset=windows-1251` had each Cyrillic
  letter's two UTF-8 bytes rendered as two separate Windows-1251 characters, so a channel list read
  `Liberty РЎРїРѕСЂС‚` instead of `Liberty Спорт`. The declaration is now a hint that loses to the
  bytes: a BOM wins outright, then valid UTF-8 (which genuine Windows-1251 Cyrillic essentially never
  is, since its letters sit in 0xC0-0xFF where UTF-8 requires 0x80-0xBF), and only then the
  declaration. The symmetric case is fixed too - a server claiming UTF-8 over bytes that are not
  valid UTF-8 is disbelieved rather than yielding a string full of U+FFFD. Playlists imported from a
  file were never affected; they already sniffed the bytes.
- **A playlist restored from an old cache snapshot could not be refreshed at all.** The pre-`sourceUrl`
  snapshot format restores with a null url (`PlaylistSnapshotCodec.decodeV1`), and that null was
  taken to mean "nothing to re-fetch": Home hides its refresh button and `refreshPlaylist()` had
  nothing to work from, so the only way back to a live copy was deleting the source and pasting the
  url in again - even though the url was sitting in the saved `PlaylistSource` the whole time. It is
  now read from there. A file import keeps its null, which is the one case where the field genuinely
  means what it said.

### Fixed - UI

- **The "Preparing your channels…" banner covered the screen title while it was showing.** It was an
  overlay pinned to the top of a Box that also held the whole scaffold, so it was simply painted
  over whatever was underneath: measured at 108dp of overlap, which cut "UA Cast Player" in half on
  Home and hid Settings' first section header entirely. It now lives inside the top bar, so the
  Scaffold measures it and the content gets the remaining height - the banner pushes rather than
  covers. It expands and shrinks instead of sliding, since an animation that only moved the banner
  would leave the content below it jumping to its new position in a single frame.
- **Picture-in-Picture always used a 16:9 window**, whatever the channel actually was - so 4:3 SD
  channels, of which a Ukrainian playlist has plenty, played letterboxed inside an already-small
  floating window. The ratio now comes from the decoded video, including its *pixel* aspect ratio:
  broadcast SD is routinely anamorphic (720x576 samples carrying a 4:3 picture), so the sample
  dimensions alone would describe a shape the viewer never sees. It is clamped into the 1:2.39..2.39:1
  range Android accepts, which also means a corrupt reported size can no longer crash the app on the
  PiP button.
- **PiP is now entered by gesturing home**, not only from the button, and animates out of the video
  rather than cross-fading from nowhere (`setAutoEnterEnabled` + `setSourceRectHint`, API 31+). Only
  while a channel is actually playing in fullscreen - auto-entering PiP for a paused, errored or
  cast-only player would put an empty window on screen.

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
- **A successfully-served segment is logged, with its byte count.** Only the remux path reported
  its segments; an ordinary passthrough logged nothing unless the upstream returned a non-2xx. So
  the central question about a failing cast - did the receiver ever pull any media, or only the
  playlist? - could not be answered from a field capture at all, and two of them were lost to it.
  The count is accumulated during the copy and reported from a `finally`, so a receiver that takes
  part of a segment and hangs up is distinguishable from one that never read a byte.
- A request for a resource this session never registered is logged instead of a silent 404.
- Proxy connection errors say which phase they happened in and, where known, the path. A peer that
  connects and hangs up without sending a request - a routine reachability probe - is no longer
  reported as a warning.

### Testing

- **Screenshot tests now run** (Roborazzi + Robolectric 4.16), covering the design system's empty
  state in both themes. `recordRoborazziDebug` regenerates goldens, `verifyRoborazziDebug` fails on
  a pixel diff. This had been shelved as blocked: the real obstacle was that Robolectric fetches its
  own 203MB `android-all` runtime jar over its own HTTP client, which fails here with an
  SSLHandshakeException while Gradle resolves the identical artifact from the identical host without
  trouble. Gradle now fetches it and Robolectric runs offline against the Gradle cache directory.
  See `docs/SCREENSHOT_TESTING.md`, including why CI enforcement is a separate decision.
- `testOptions.unitTests.isIncludeAndroidResources` is now `true`, which screenshot tests require.
  All 847 unit tests pass with it on.
- **The instrumented lifecycle suite passes again - all 6, up from 1.** Three failures shared one
  cause and one had its own:
  - `FakeOriginServer` serves bytes no decoder accepts (these tests are about the player's
    *lifecycle*, not decoding), so every channel fails fatally. With auto-skip on that is not a
    static failure to assert against: the player marks the channel dead and advances, so a test that
    opened "Channel 1" found itself on "Channel 3", and once all three were exhausted there was no
    player left to inspect. The fixture now turns auto-skip off - before the player is opened, since
    `PlayerViewModel` reads the flag once at construction - and restores it afterwards.
  - The channel-switch test looked up "Next" by content description, which only the *fullscreen*
    overlay uses; the player opens inline, where the same control is a `PillButton` exposing its
    label as text with `contentDescription = null`. The lookup could never have matched from that
    state, and failed as "could not find any node" - indistinguishable from the player being gone.
  - The mini/fullscreen test pressed system back and nothing happened, because the search field
    still had focus behind the player and **the IME swallows the back key to dismiss itself** before
    the app's `BackHandler` ever runs. The helper now closes the keyboard after opening a channel.
- **The suite no longer destroys the data of whoever it runs against.** `EmptyPlaylistInstrumentedTest`
  clears `uacast_prefs` and every playlist file to get a genuinely fresh start, and never put any of
  it back - on a developer's phone that is the same install they actually watch TV on, so it deleted
  their configured playlist, EPG source and settings for real. It happened twice during this suite's
  own repair. The state is now captured before the wipe and restored in `@After`, pass or fail, using
  the same predicate the wipe deletes by so the two cannot drift apart.
- **The banner overlap above is covered by a layout assertion, not a golden image.** `RootTopBar`
  was extracted from `RootScaffold`'s `topBar` lambda so it can be composed on its own (the whole
  scaffold would mean supplying sixty-odd parameters), and `RootTopBarLayoutTest` asserts the
  banner's bottom edge is above the title row's top edge - plus that the title moves back up once
  the download finishes, which catches a hidden banner that still reserves its height. Checked
  against the old layout before being kept: it fails there with "Banner (bottom=120.0.dp) overlaps
  the title row (top=12.0.dp)".

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
