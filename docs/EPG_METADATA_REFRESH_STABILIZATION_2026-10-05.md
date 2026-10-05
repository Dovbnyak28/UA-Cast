# EPG metadata refresh stabilization — 5 October 2026

## Confirmed UI state defect (P2)

Three displayed programme lookups used `remember(channel.streamUrl, data, time)`.
EpgIndex.match also consumes tvg-id, tvg-name and display name. Updating these
metadata while retaining the URL therefore rendered a new channel with the old
programme until another EPG/time key changed.

| Surface | Reproduced execution path |
| --- | --- |
| Single-group channel row | Refreshed playlist resolves the same open group; lazy item keeps its index/URL key; new metadata reaches ChannelRow while the programme memo remains old. |
| Home continue-watching | A channel without tvg-id keeps its name/URL FavoriteKey while tvg-name changes; HomeContentPolicy still matches it, but ContinueWatchingCard retains the old programme. |
| Mini player | PlayerViewModel receives new channel metadata at the same URL; the mounted MiniPlayerBar updates the channel title but not its programme. |

Changing an explicit tvg-id may legitimately remove Home's continue-watching
card because its favorite identity no longer matches. That behavior is not
reported as a bug; the Home regression changes tvg-name with unchanged identity.

## Minimal correction

ChannelListSection, HomeScreen and MiniPlayerBar now key the existing memo on
the immutable M3uChannel value, EPG data and time. Equal channel values still
reuse the result; changed metadata recomputes the existing indexed lookup.
No additional collectors, timers, network requests, cache entries, parser
changes, playback commands or dependencies are introduced.

Five integration regressions exercise the actual three surfaces. The row cases
independently change tvg-id, tvg-name and display name. All five displayed the
old programme before the update and then failed to show the new programme on
the old implementation. All five pass after the three-key correction with the
same data/time. The mini-player fixture uses a refused loopback endpoint, not
a real provider; this proves metadata/UI invalidation, not stream decoding.

## Verification boundaries

The PlayerHost-only complete gate already passed separately (Debug 2634,
Release/Play 2270 each, all 49 unchanged goldens). The subsequent focused run
passed all twenty cases in 2m1s task execution: five EPG cases, four PlayerHost
cases and eleven OpenTransform cases, together with lintDebug and detekt.
The final complete isolated regression gate succeeded in 10m16s (227 tasks:
23 executed, 204 up-to-date): Debug 2639, Release/Play 2270 each, zero test
failures/errors/skips. Debug results and their 49 unchanged golden comparisons
were reused from the completed Debug phase of the same-source previous attempt;
that attempt was interrupted during Release and is not itself called successful.
Unchanged Core 133 results were also reused. Release and Play tests completed in
the resumed gate. Lint has zero errors/warnings (one existing Hint per variant),
detekt passes, debug/test APKs assemble, and both app/harness benchmark Kotlin
variants compile. The six Kotlin files were byte-verified identical between the
primary checkout and the isolated review checkout. All twelve repository checks,
eight performance-validator cases and six runner contract/privacy cases pass.

The previous 21c3be3 Android CI passed all six jobs, but its performance run
lost ADB before producing complete JSON. Those outcomes do not certify this
new revision. Three previously measured frame-budget exceedances remain open;
physical Mi TV/provider acceptance is still unavailable. No frame gain, release,
merge, version bump or computer shutdown is claimed/performed by this change.
