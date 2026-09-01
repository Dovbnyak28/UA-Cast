# Performance: thread rules

Motivated by a real complaint on a 2863-channel, 11-group playlist: the whole app froze for the
duration of every playlist load. The root cause and the fixes below are collectively "Block 1-3" in
the UI-jank fix pass; see the git log for the exact commits.

## The rule

`AppViewModel` hands every controller under `com.uacastplayer.app` its `viewModelScope`, which runs
on `Dispatchers.Main.immediate` by default. That means **`scope.launch { ... }` alone puts you on the
main thread**, not "somewhere in the background" - `viewModelScope` is not itself a signal that work
is off the UI thread. Every controller and repository in this codebase follows one rule instead:

> Any `suspend fun` that touches a file, the network, or does CPU-bound work (parsing, sorting,
> building a big collection) must wrap *that work* in its own `withContext(...)` - never assume the
> caller's dispatcher is already safe to block.

Which dispatcher:
- **`Dispatchers.IO`** - reading/writing a file, opening a network connection. Blocking-but-cheap-per-thread work.
- **`Dispatchers.Default`** - parsing text, sorting/grouping/deduplicating a large collection. CPU-bound work that would otherwise tie up an IO-pool thread doing no I/O.

A `suspend fun` that only calls other already-dispatched suspend functions (e.g. `PlaylistController`
methods, which just call into `PlaylistRepository`) needs no `withContext` of its own - the dispatch
happens at the lowest level that actually does the blocking/CPU work, once, not at every layer above it.

## What this caught

- `PlaylistRepository.toOutcome` called `M3uParser.parse` + `ChannelGrouper.group` directly, with no
  dispatcher hop - on a large playlist (thousands of channels) this froze the UI for the entire parse.
  Every other file/parse/serialize path in `PlaylistRepository`, `EpgRepository`, `BackupController`,
  `PlaylistSourceStore`, `GroupVisibilityStore`, and `FavoritesRepository` was already correctly
  wrapped - this was the one gap. Fixed by wrapping the parse+group work in `withContext(Dispatchers.Default)`.
- `PlaylistRepository.restoreSnapshot` did its (IO-dispatched) file read and its (CPU-bound) grouping
  in the same `withContext(Dispatchers.IO)` block - not a UI freeze (IO isn't the main thread either),
  but the wrong dispatcher for CPU work. Split into its own `Dispatchers.Default` hop for consistency.

See `PlaylistParsePerformanceTest` for two regression guards: 3000 channels stay well under the
interactive budget, and a provider-scale 40,000-channel fixture must parse and group within a loose
8-second shared-runner ceiling. The second test is intentionally large enough to expose O(n²)
behaviour while remaining stable in the ordinary unit-test gate.

## Background prefetch isn't exempt either

Being off the main thread doesn't mean "free to run unbounded." `IconPrefetcher` correctly runs on
background dispatchers via `IconRepository`'s `withContext(Dispatchers.IO)` fetch calls, but on a
2863-channel playlist it used to queue **all 2863** icon fetches (just throttled to 6 concurrent) -
minutes of background network/disk activity competing with playback and scrolling for CPU, GC
pressure, and the disk. `PrefetchSelectionPolicy` now caps a single pass to the channels actually
likely to be seen soon (favorites, last-watched, first group - see its doc comment), gated off
entirely while something is actually playing/casting (`PlaybackActivity`), and skipped altogether on
`DeviceTier.LOW_END`. Anything outside that selection still gets its icon lazily, one row at a time,
the first time it's actually scrolled into view - that path was already correct.

The selected pass is drained by a fixed six-worker queue. Equal icon candidate chains are collapsed
before any coroutine is launched, with each unique item carrying a progress weight, so a 40k-channel
playlist cannot create 40k suspended `async` objects or fetch the same logo repeatedly. A pass that
the connectivity/Wi-Fi-only gate refuses reports a distinct not-executed outcome: the controller
clears the transient progress state but does not advance `completedRuns`, update the last-prefetch
timestamp, or dismiss the refresh reminder for work that never happened.

## Diagnostics stay inside a fixed memory budget

`LogcatReader` streams the process log into a bounded tail instead of materialising the complete
`logcat -d` output and trimming it afterwards. At most 4,000 complete lines and 512K characters are
retained while reading; an oversized individual line is clipped to its newest tail. This matters on
low-memory devices because diagnostics are commonly generated immediately after a playback failure,
when Media3 and proxy logging are at their busiest and the player still owns its buffers.

## Device benchmarks

The `:baselineprofile` module contains the profile generator plus deterministic device
Macrobenchmarks. They require an API 33+ connected device or emulator:

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uacastplayer.baselineprofile.StartupBenchmark
```

Startup results belong in release evidence rather than unit-test timing: Macrobenchmark controls
process state and compilation mode on the target device, which a desktop JVM test cannot emulate.
`CriticalJourneysBenchmark` adds peak-memory/frame measurements for a 40,000-channel restore/open,
first player launch, fullscreen and EPG guide. `EpgParseBenchmark` runs the production SAX +
retention + heap-budget + index pipeline over 350,000 synthetic programmes and reports both peak
memory and the `UaCastEpgParseAndIndex` trace duration. The fixture is credential-free and compiled
only into `benchmarkRelease`/`nonMinifiedRelease`; exact commands and the destructive-data warning
are in `docs/RELEASING.md`.

## The EPG snapshot no longer stores XML

Measured on a Mi A2 (API 30) against a real 500-channel Ukrainian feed, restoring the cached guide
took **53 seconds** of background CPU on every cold start:

```
PROBE restoreSnapshot took 53007ms, 676 channels, 250000 programmes
```

The cache held the raw XMLTV document as downloaded - 44.9MB - so every launch re-inflated and
re-parsed all of it to rebuild data the app had already had the previous time. Nothing read the raw
XML any more: `<desc>` was dropped from `EpgProgramme` precisely because nothing displayed it, so the
document was being carried at full price for no reader.

`EpgSnapshotCodec` v2 stores the parsed guide instead. Same device, same feed, same 676 channels and
250,000 programmes:

| | v1 (XMLTV document) | v2 (parsed) |
|---|---|---|
| snapshot on disk | 44.9 MB | **14.1 MB** |
| restore | 53.0 s | **6.6 s** |

The layout leans on the shape the data already has: programmes are grouped by channel id, so the id
is written once per group rather than once per programme, and on decode every programme in a group
shares that one `String` instance - the same channel-id pooling the in-memory representation relies
on, preserved across the file instead of rebuilt on load.

v1 snapshots stay readable, parsed once and immediately rewritten as v2, so upgrading does not throw
away a guide the user already has. That one launch still pays the 53 seconds; every launch after it
pays 6.6.

Guide initialization is also deferred until a playlist is actually available. An empty fresh
install therefore pays neither the initial XMLTV download nor the parse/restore cost; selecting or
restoring a playlist starts the one idempotent initial guide load.

`EpgSnapshotSizeTest` guards the decode-vs-parse margin. It deliberately asserts **nothing about file
size**: synthetic titles are near-identical, so gzip crushes a generated XMLTV document about
eighteenfold (41KB against 737KB for the binary) and such a test measures the fixture, not the
format. What actually shrinks a real file is dropping `<desc>`, which no honest synthetic fixture
here reproduces - hence the end-to-end device measurement above.

### Note the caps while you are here

The original field run hit the then-current 250,000-programme cap *exactly*, which was not a
coincidence: the feed was being cut off. XMLTV is normally ordered by channel, so a cap does not
thin the guide evenly; it leaves later channels with nothing. The hard safety backstop is now
400,000, but production first applies the three-day retention window and then
`HeapBudget.maxProgrammes(Runtime.maxMemory())`, so a 128MB heap deliberately keeps far less than a
roomy device. Any real truncation reaches `EpgData.truncation`, is logged, and is shown in Settings
under the source picker. `EpgParseBenchmark` exercises that exact device-specific decision.
