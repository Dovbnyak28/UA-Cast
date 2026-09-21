# EPG strategy

## Sources

Five `epg.it999.ru` variants (`epg/EpgSource.kt`), user-selectable in Settings, persisted. Three are
gzip (`epg2.xml.gz`, `epg.xml.gz`, `pp.xml.gz`); two are the same simplified feeds already inflated,
served as plain XML (`epg2.xml`, `epg.xml`). Cleartext (`http://`) is intentional - like most
IPTV/EPG providers this host serves plain HTTP, and it's already permitted by the network security
config.

## Download and parsing

- The document is downloaded and stored **as-is** (`data/epg/EpgDownloader.kt`, 96MB cap via
  `BoundedByteReader`) - gzip sources are only inflated (`GZIPInputStream`) at the moment they're
  parsed, never eagerly. `core/io/GzipSniffer.kt` checks the magic bytes rather than trusting the
  URL's extension, since two of the five sources are already plain XML.
- Parsing (`epg/XmlTvParser.kt`) is SAX-based and hardened against XXE: secure processing is
  requested, external general/parameter entities are disabled, and the entity resolver returns
  nothing for any external reference. A `DOCTYPE` declaration is still **allowed** - real feeds
  routinely have one, and rejecting the document outright would break them for no security benefit
  once external entities are already disabled.
- **Only a three-day window is kept, and it is applied as the document streams past**
  (`epg/EpgRetentionPolicy.kt`). Both ends exist for the same reason, and both were found the same
  way - by measuring what the cap was actually being spent on.
  - The **near** end is the start of the local calendar day, not "now", because `DayScheduleBuilder`
    draws the whole day. Measured on the shipped feed: 793,417 programmes, of which 388,863 were
    already-broadcast television.
  - The **far** end is midnight opening the third day (`DAYS_KEPT = 3`). Feeds carry about eight
    days; this app has exactly two consumers of programme data - `ProgrammeLookup` (now/next) and
    `DayScheduleBuilder` (today) - and no screen anywhere offers tomorrow, let alone day eight.
    Three rather than one because the guide has to outlive its own refresh on a device that does not
    see Wi-Fi (see below); three rather than more because that is what stops the cap being reached.
  - Both ends are calendar arithmetic (`LocalDate.plusDays`), never `n * 24h`: Europe/Kyiv's October
    Sunday is 25 hours long, and the same mistake in `DayScheduleBuilder` silently dropped an hour
    of that evening's listings.
  - `<desc>` is not accumulated at all.
- Hard caps: 25,000 channels, 400,000 programmes, 512 characters per title/display-name. A feed that
  exceeds these is truncated, not rejected - the caps exist to bound worst-case memory/CPU, not to
  validate feed "correctness". The text cap is sized for names, which are the only text kept now
  that descriptions are skipped.
  - **The programme cap is a backstop, not an operating limit.** It was sized (346,837, rounded up)
    when retention only dropped the past and the whole eight-day window was kept. With the far end
    in place a three-day window of the shipped feed is roughly 130,000, so an ordinary feed should
    never come near it. A field report is what proved the difference matters: a 311-channel playlist
    whose guide carried 4052 channels stopped dead on 400,000 exactly. Truncation is count-based and
    in document order, so it does not thin every channel's guide evenly - it gives the channels at
    the end of the file **nothing at all**, which presents as "the TV guide is missing my channels".
- Timestamps are `yyyyMMddHHmmss ±ZZZZ` (`epg/XmlTvTimeParser.kt`), converted by plain arithmetic
  (Howard Hinnant's `days_from_civil`) rather than by `java.util.Calendar` - the Calendar version
  allocated per call and synchronized on a shared zone lookup, which a field capture caught holding
  the EPG worker for 1.2s at a stretch.

## Caching and refresh

The parsed guide - not the XMLTV document it came from - is written to `epg_snapshot.bin`
(`data/epg/EpgSnapshotStore.kt`, `epg/EpgSnapshotCodec.kt`) and restored when the first playlist is
available during startup, which is what makes the guide available offline and quickly. A fresh
install with no playlist deliberately does not fetch or parse a guide: there are no channels to
match it to, so that work would only consume network, CPU and heap while showing a misleading
progress banner over the empty state.

**A restored snapshot from an earlier day is refreshed in the background, on an unmetered network
only** (`epg/EpgRefreshPolicy.kt`, `EpgController.refreshIfFromAnEarlierDay`). The header's
`savedAtEpochMillis` is what decides; it was written and read back by the codec for a long time
before any logic consulted it, and until it did, `EpgController.loadInitial` fetched only when there
was no snapshot at all. A device that downloaded the guide once kept it until the feed's window had
passed, after which the sheet went empty and the now/next badges disappeared - which presents as
"the TV guide stopped working", never as a staleness problem.

The refresh never sets `hasError`: a background attempt the user did not ask for must not replace a
cached guide that still works with an error state. A failure keeps the cache and logs the reason.

Unmetered-only is a deliberate cost decision - the download is tens of megabytes and the app must
not spend somebody's mobile data on its own initiative. **The consequence, stated plainly:** a phone
that never sees Wi-Fi keeps its guide for `DAYS_KEPT` days and then has none, and nothing currently
tells the user why. The two settings are coupled - shortening the retention window shortens how long
an un-refreshed guide lasts - so they should be changed together or not at all.

## Matching an M3U channel to an EPG channel

`epg/EpgIndex.kt` tries, in order:

1. Exact `tvg-id` match.
2. Normalized `tvg-id` match (case-folded, NFKC).
3. Normalized `tvg-name` match.
4. Normalized display-name match.

Normalization (`epg/EpgChannelNameNormalizer.kt`) is NFKC + stripping a trailing quality marker
(`HD`/`FHD`/`UHD`/`4K`/`SD`, bracketed or not) + a small regional-alias dictionary (e.g. Cyrillic
ё/е folding) + case-folding. The first signal that matches wins; there's no scoring or fuzzy
matching beyond this fixed chain.

## Current/next lookup

`epg/ProgrammeLookup.kt` binary-searches a channel's programme list (must be pre-sorted by start
time) for the last programme whose start is `<= now`. The **effective stop** of that programme is
always the next programme's start time, never its own declared stop - real feeds routinely have
small gaps or overlaps between a programme's declared stop and the next one's start, and the start
times are the more reliable signal. `epg/ProgrammeProgress.kt` turns `(start, effectiveStop, now)`
into a clamped 0..1 progress fraction for the UI's progress bar, which ticks every 30 seconds
(`AppViewModel`'s EPG tick loop) - not on every recomposition.

## The day's lineup

`epg/DayScheduleBuilder.kt` builds what the guide sheet draws: one channel's programmes for the
local calendar day containing `now`, as `past` / `current` / `upcoming`.

Two rules about it are easy to lose and were both lost once.

**The three lists must partition the day.** They are everything `ui/epg/EpgGuideSheet.kt` draws, so
a programme in none of them is one nobody can see. They were once three independent filters - has
finished, is the first one airing, starts later - which only partition a day with at most one
programme on at a time. Overlapping listings are ordinary (the same fact `ProgrammeLookup` above
exists to work around), and the second of two overlapping programmes matched none of the three. The
rule now is a single split: finished is the past, the first unfinished programme that has already
begun is on air, everything else still to finish is upcoming.

**A start time is not a key.** The guide sheet's rows live in a `LazyColumn`, which throws
`IllegalArgumentException` out of composition on a repeated key rather than drawing a duplicate row.
Nothing between the XML and that list promises start times differ: `XmlTvParser` keeps every
`<programme>` element it is handed, `EpgRepository` only sorts them, and a feed merging several
providers repeats entries as a matter of course. A repeat with no stop time is a second way in,
since `EpgProgramme` falls back to the start for its stop. Row keys therefore carry the position as
well as the start time.
