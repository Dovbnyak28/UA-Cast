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
- Hard caps: 25,000 channels, 250,000 programmes, 16KB of text per title/description. A feed that
  exceeds these is truncated, not rejected - the caps exist to bound worst-case memory/CPU, not to
  validate feed "correctness".
- Timestamps are `yyyyMMddHHmmss ±ZZZZ` (`epg/XmlTvTimeParser.kt`), parsed by hand via
  `java.util.Calendar` rather than `java.time` - the latter needs API 26+ or core library
  desugaring, and this app's `minSdk` is 23.

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
