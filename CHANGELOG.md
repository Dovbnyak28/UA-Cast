# Changelog

Versions are marked in two places, which must move together: the local defaults in
`app/build.gradle.kts` and `UACAST_VERSION_NAME` in `.github/workflows/android-ci.yml`. CI appends
its run number to both (see `docs/RELEASING.md`), so a CI artifact reads `0.9.0.<run>` with a
`versionCode` of the run number - the values below are what a local build produces.

## 0.9.0 - unreleased

`versionCode` 9. Renumbered from 0.3.0 while still unreleased, and the jump past 0.4-0.8 is
deliberate rather than an accident of counting: what this section describes is not a third
increment on a small app but the app becoming the thing it set out to be - playlists, EPG,
favorites, Chromecast, DLNA, the local proxy, three themes and four languages, verified on real
hardware rather than only an emulator. 0.3.0 would have told a user holding the APK that they were
early in a series. They are not; they are one step short of 1.0.0, and the number should say so.

**Why not 1.0.0.** Only one person has ever run this app. 1.0.0 is a claim about behaviour holding
still for other people, and nothing here has been exposed to a device, a playlist or a TV that was
not chosen by its author. That claim is earned by other people's devices, not by more commits.
See `docs/RELEASING.md` for what has to be true before the major version moves.

### Added

- **DLNA/UPnP casting** to any AVTransport renderer on the LAN - typically an older Smart TV with
  no Chromecast support. Own SSDP discovery and SOAP client, no new dependencies, reusing the same
  local HLS proxy Chromecast already goes through. See `docs/DLNA.md` for what this MVP does and
  does not do.
- **Channel switching while casting to DLNA.** The renderer is re-pointed at the new channel
  instead of being left on the old one.
- **A third theme, Midnight** - true black, no wallpaper texture, and a deliberately muted pewter
  accent. Azure and Cinema differ in temperature but both paint the same faint texture a few percent
  above black; Midnight takes the axis they leave open, which on an OLED panel is the one that costs
  no backlight. The accent is desaturated on purpose: a saturated colour on true black has no
  ambient tone to sit against and simply glows, so here colour is reserved for the three
  route-health states and chrome stays neutral. Selectable in Settings alongside the other two.

### Fixed - contrast

- **The EPG "playlist too large" warning in Settings was drawn in a glow colour**, not a text one -
  amber at 50% alpha, which composites to a dull olive against the background instead of covering
  it: 3.99:1, under WCAG AA. It now uses the full-opacity route amber. Present since the warning was
  added; the true-black theme is what made it visible rather than merely dim.
  `scripts/check-glow-not-text.sh` fails the build on any future `*Glow` used as a text or icon
  colour.

### Fixed - launcher icon

- **The launcher was showing a cropped fragment of the app icon.** The adaptive icon's foreground
  was the full-bleed artwork, spanning ~100dp of the 108dp canvas - but a launcher only ever shows
  the inner 72dp of that canvas. The blue and yellow ring was cut off entirely and the glyph was
  clipped on two sides; visible on any device with a circular mask, which is most of them. The
  foreground is now the badge alone at 72dp, so the whole mark survives every mask shape. Nothing
  was redrawn - every asset here is a crop, a scale or a mask of the original artwork.
- **Themed icons (Android 13+) had nothing to theme.** There was no `<monochrome>` layer, so the app
  kept its full-colour icon while the rest of the launcher followed the wallpaper. Added, derived
  from the mark's own luminance.
- **The round and square legacy icons were the same file**, and both filled every pixel of their
  square. They are now genuinely different shapes with transparent corners - which only matters on
  API 24-25, the range the adaptive icon does not cover.

### Fixed - platform

- **The cast notification never appeared on Android 13 or newer.** `CastProxyService` is a
  foreground service that posts a notification and holds a partial wake lock plus a Wi-Fi lock for
  the whole cast, but `POST_NOTIFICATIONS` was neither declared nor requested - and at `targetSdk`
  36 the system drops the notification silently. The service still ran, so the user was left with
  something keeping the CPU awake that they could neither see nor stop from outside the app. Now
  declared, and requested at the moment a cast actually starts (`ui/permissions/`) rather than on
  first launch, so the notification that follows a second later is the answer to why it was asked.
  Not reproducible on the test hardware here (Android 11) - found by reading the manifest against
  the target API, not by running it.
- **Edge-to-edge is now opted into rather than waited for.** `android:statusBarColor` and
  `navigationBarColor` are deprecated and become no-ops at `targetSdk` 35, where the system forces
  edge-to-edge regardless. `enableEdgeToEdge()` makes that the behaviour on every API level, so a
  missing `windowInsetsPadding` shows up on any test device instead of only on Android 15 hardware -
  which immediately found one: the language picker, the first screen a new install shows, had no
  inset handling at all. The two dead theme attributes are gone; they read as the thing controlling
  the system bars while controlling nothing.

### Fixed - localization

- **The home screen counters were ungrammatical in Ukrainian and Russian.** The count is rendered
  above its label as two separate pieces of text, and the label was a fixed genitive plural - so the
  app's main screen in its primary language read "1 Улюблених" and "2863 Каналів". They are plurals
  now, with all four quantity classes. The counts worth testing are not 1 and 5 but 21 and 22, where
  Slavic plural rules stop agreeing with the English intuition that "one" means one; a test asserts
  the declensions directly. The screenshot goldens also now cover the dashboard *with data in it*
  (Ukrainian and English) - the only ones that existed were empty states, which is precisely how a
  bug about numbers went unseen.

### Changed - build

- **Lint warnings now fail the build.** The project carried 97 of them, and things were genuinely
  lost in that list: a wrong `@VisibleForTesting` on an API production calls, eight dead colors, a
  Compose `Modifier` parameter in the wrong position, and an animated `Modifier.offset` using the
  value overload - which recomposed a segmented control on every frame of its slide. All fixed;
  every check deliberately not obeyed is named, with its reasoning, in `app/lint.xml`.

### Fixed - security

- **Credentials embedded in a url no longer leak into a diagnostics report.** `LogSanitizer`
  redacted through `URI.getAuthority`, which includes userinfo, so
  `http://user:pass@host/path` came out with `user:pass` intact - in the one component whose
  purpose is keeping credentials out of a report the user shares. It now keeps only scheme, host
  and port, and redacts a url whole when the host cannot be parsed.

### Fixed - casting

- **A channel casting through the proxy could reload itself to death without ever playing.** The
  stall watchdog asked "has the receiver reported PLAYING within 4 seconds", which on the proxy path
  it cannot: every byte goes origin → phone → receiver, and one segment of an HD channel measured
  3.6-6.5MB taking 2-3 seconds to move, so a receiver buffering two of them is still well inside the
  timeout. A device capture caught it firing **four times in 30 seconds** on one channel while the
  proxy was delivering a complete 3.6MB segment roughly every 2 seconds without a gap - and each
  firing forced a reload that aborted the in-flight transfers, so every attempt started further
  behind than the last. That channel never played. It now decides from *bytes delivered* instead of
  elapsed time (`cast/CastStallWatchdogPolicy.kt`): a tick where the proxy served the receiver
  nothing at all is still a stall and still fires immediately, but a receiver visibly pulling media
  is left alone, up to a 30s ceiling for the case of one that fetches forever and never plays. Same
  channel, same receiver, verified on device: zero firings, plays in ~9s. The direct-mode watchdog is
  a different question with a different answer and keeps its flat 4s.
- **Every cast paid 4 seconds of dead air for a direct attempt already known to fail.** The store
  that exists to skip the direct route for a (stream, receiver) pair was only ever written for a
  confirmed MPEG-2 verdict - the one case `CastRecoveryPolicy` answers with `GiveUp` - so the
  overwhelmingly common reason a cast lands on the proxy, the direct watchdog simply timing out, was
  never remembered. A device capture showed seven consecutive channel loads and seven identical
  `Falling back to proxy: watchdog_timeout` lines, including two visits to the same channel a minute
  apart. It is now recorded when the direct attempt never played *and the proxy then did*
  (`cast/DirectRouteMemoryPolicy.kt`) - proof that the stream, receiver and network are all fine and
  only the direct route is not, rather than one bad moment sending a channel through the phone for
  30 days. Verified on device: second visit to the same channel goes straight to the proxy, no
  watchdog line.
- **The receiver showed a bare title on a black screen, and the cast dialog a large empty grey
  panel.** The load request carried no image at all; it now sends the channel's `tvg-logo` as
  artwork. Partial: channels whose icon comes from the EPG or the CDN fallback rather than from
  `tvg-logo` still send none, because that resolution chain (`icons/IconResolver`) lives in
  `AppViewModel` and is not reachable from where the cast load is built. `cast load: artwork=` in
  the log says which case a given channel hit.
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

### Fixed - EPG

- **The TV guide was being silently cut off.** `XmlTvParser` caps a feed at 250,000 programmes, and
  a real 500-channel Ukrainian feed hits that *exactly* - XMLTV is normally ordered by channel, so
  the cap does not thin the guide out evenly, it leaves the last channels in the file with no
  programmes at all. The parser had computed `programmeLimitExceeded` since it was written and
  **nothing anywhere read it**: the flag went into `XmlTvParseResult` and died there, the only
  references outside the parser being two `assertFalse`s in its own test. It now reaches
  `EpgData.truncation`, is logged, and is shown in Settings directly under the source picker - where
  the actionable response, choosing a simplified source, already is.

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

- **The player's title bar said "UA Cast Player" instead of naming the channel.** On the one screen
  where the user is least in doubt about which app they are in and most in doubt about what is
  playing, it showed the app name - while the channel it was playing sat in a card further down.
  It now shows the channel, ellipsised, falling back to the app name only before anything is loaded.
- **Ukrainian broke a player label in the middle of a word.** The quick-settings row gives each item
  an equal share of the width, which is what stops labels wrapping character-by-character - but
  weights cannot create a place to wrap, and `"Співвідношення"` is one 14-character word, so it came
  out as `Співвідно / шення`. `docs/DESIGN_SYSTEM.md` cited that very string as an example of a label
  that wraps *cleanly* under the rule; the rule is now stated in terms of the longest **word**, and
  the label is `"Формат кадру"` (Russian likewise).
- **Two-line labels in that row had their descenders cut off.** The label box was a fixed 32dp
  height, less 6dp of top padding, against roughly 29dp needed for two lines of 12sp - so the second
  line was drawn into space that did not exist and `"кадру"` lost the tail of its `у`. It is a
  minimum height now, which keeps one-line labels reserving the same space (so the icons above stay
  aligned) while letting a two-line label take the room it needs.

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
- **Screenshot verification runs in CI**, as `:app:verifyRoborazziDebug` - which is
  `testDebugUnitTest` with golden verification on, so it covers the whole debug suite in one pass
  rather than running it twice. A failure uploads the diffs as an artifact, since the goldens were
  recorded on Windows and the runner is Linux; see `docs/SCREENSHOT_TESTING.md`.
- **CI moved from JDK 17 to 21.** Robolectric records a required Java version per Android API level
  and API 36 demands 21 (`DefaultSdkProvider`'s entry is `("16", "13921718", "REL", 21)`); on 17
  every Robolectric test refuses to start, so the screenshot tests could never have run there. The
  app is still compiled to Java 17 bytecode - this changes what Gradle runs on, not what ships.
- **`testReleaseUnitTest` now skips the Compose-rule tests instead of failing on them.**
  `createComposeRule()` launches `androidx.activity.ComponentActivity`, declared only by
  `compose-ui-test-manifest` - a `debugImplementation` dependency, so the entry is in the debug
  merged manifest and nowhere else. Under the release variant they died at rule setup with "Unable
  to resolve activity for Intent ... ComponentActivity", which names no variant and reads like a
  broken test. Excluded by JUnit category rather than class-name pattern, so renaming or moving a
  test cannot quietly return it to the release run: debug runs 868, release 864, and the difference
  is exactly those four.
- **Three CI steps that had never actually passed now do.** Nothing had ever run this workflow (the
  repository has no remote), so its guard scripts had drifted: `check-locale-format` flagged a
  *comment* in `Fingerprint.kt` that accurately documents the `"%02x".format(...)` loop it replaced
  - prose that mentions a format call is not a format call, so the script now skips comment lines -
  and a genuinely locale-sensitive human-read label in the audio-track sheet, which is now marked
  `// locale-ok`. `check-applog-sensitive-vars` flagged a DLNA SOAP failure log interpolating a raw
  control URL; that is not a false positive, so the URL is gone from the message rather than
  annotated away.
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
- **Restoring the cached TV guide: 53s → 6.6s, and 44.9MB → 14.1MB on disk.** Measured on a Mi A2
  against a real feed, same 676 channels and 250,000 programmes both ways. The cache held the raw
  XMLTV document as downloaded, so every single cold start re-inflated and re-parsed all 44.9MB of
  it to rebuild data the app had already had the previous time - and nothing read the raw XML any
  more, since `<desc>` was dropped from `EpgProgramme` precisely because nothing displayed it.
  `EpgSnapshotCodec` v2 stores the parsed guide instead, laid out so each channel id is written once
  per group rather than once per programme and every programme in a group shares that one `String`
  on decode. Snapshots written by the previous version stay readable - parsed once, immediately
  rewritten in the new format - so upgrading never discards a guide the user already has. See
  `docs/PERFORMANCE.md`.
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
