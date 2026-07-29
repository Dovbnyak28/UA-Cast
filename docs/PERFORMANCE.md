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

See `PlaylistParsePerformanceTest` for a regression guard: parsing+grouping 3000 channels is asserted
to complete in well under 2 seconds, so a future accidental main-thread reintroduction (or an O(n)→O(n²)
regression) shows up as a slow/failing test, not just as a support complaint.

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
