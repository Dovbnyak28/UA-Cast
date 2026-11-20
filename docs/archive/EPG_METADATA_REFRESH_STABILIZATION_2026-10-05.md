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

## EPG-fix remote evidence: 853f4d2

The independent [push-event Android CI](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37327512214)
passed all six jobs for 853f4d2. Downloaded native runner artifacts report
148/151/151 suite cases for API 24/30/36: 142/145/145 passed, zero failed and
six assumption-skipped cases per API. Three additional status-zero messages are
diagnostics without a class/test identity, not test passes. The skipped cases
are LAN discovery, physical-TV playback, two private-playlist opt-in cases and
two Mi TV document-stub cases; they do not certify physical-TV acceptance.

The initial [PR-event run](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37327520791)
passed unit/goldens, quality, packaging and API 30. API 24/36 failed before test
execution: sdkmanager reported `Error on ZipFile unknown archive` during emulator
or system-image installation. The subsequent connection-refused cleanup was not
an application failure. After the workflow completed, one failed-jobs-only retry
was accepted for the same SHA and attempt 2 passed all six jobs. Downloaded PR
native artifacts independently confirm the same 142/145/145 passes and six
assumption skips per API. No SDK version, assertion, timeout or budget was
changed. The original failed attempt remains part of the evidence.

The [current measured performance run](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37327520841)
completed nine tests in 311.265s, zero failures/errors/skips, including the
sleeping-display regression and all eight measured journeys. Downloaded XML and
final JSON independently reproduce the unchanged validator's exit 1: three
frame-budget rules still fail. This is complete evidence, not a passing gate.

| Current-head measurement | Result | Limit |
| --- | ---: | ---: |
| Open channels frame CPU P95 | 164.146ms | 100ms |
| First player frame CPU P95 | 437.024ms | 100ms |
| Fullscreen frame CPU P95 | 396.226ms | 100ms |
| EPG guide frame CPU P95 | 58.300ms | 100ms |
| Cold / warm startup median | 1662.379 / 1353.152ms | 5000 / 2500ms |
| 40k-channel restore median | 1624.639ms | 10000ms |
| 350k EPG parse/index median | 2276.589ms | 60000ms |
| Worst EPG managed-heap metric | 101360 KiB | 262144 KiB |

Selected iteration-zero traces contain main-thread postAndWait slices of
117.922ms (channels), 291.933ms (first player) and 265.701ms (fullscreen), with
concurrent RenderThread drawing and shader compilation. The first-player
Compose:recompose slice is 55.884ms and Compose:onRemembered is 2.351ms; the
earlier trace's long onRemembered/binder observation does not identify a stable
cause across runs. Overlapping wall-clock slices must not be summed as CPU
costs. These observations do not identify a particular source function to fix,
prove a new code regression, or justify an uncontrolled before/after percentage.
No speculative production modification or performance-budget relaxation was
made in this verification pass. ADB still has no connected physical device.

The subsequent reproduced EntryStagger policy/recycling defect is separate
from these measured frame-budget failures. See
`docs/ENTRY_STAGGER_STABILIZATION_2026-10-05.md`; 853f4d2's evidence is retained
as parent-head evidence, not presented as verification of the newer source.
