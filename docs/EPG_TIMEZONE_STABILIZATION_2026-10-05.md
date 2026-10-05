# EPG device-time-zone stabilization — 5 October 2026

## Confirmed problem (P2)

`EpgGuideSheet` captured `ZoneId.systemDefault()` in an unkeyed `remember`.
That value survived ordinary recomposition, EPG clock ticks, and background /
foreground transitions while the guide remained composed. A device-zone change
therefore left both displayed programme times and local-calendar day filtering
in the obsolete zone until the user closed and reopened the guide.

Execution path: an open player/channel guide → remembered device zone → `today`
and `selectedDate` → `DayScheduleBuilder.build` → `ScheduleList` / programme time
labels. The feed uses absolute timestamps; the cached UI zone was the defect.
Neither XMLTV parsing nor EPG matching has to change or refetch the feed.

## Reproduction before implementation

Four actual Compose-guide cases ran against unchanged production code. Three
failed at the expected new-zone assertion, after proving the initial UTC
programme/time display. Closing and reopening passed as a positive control.
The cases cover a live timezone event, a change while the guide is stopped with
no event delivered, and a subsequent 30-second clock tick. UTC → Europe/Kyiv
also crosses midnight: the obsolete zone incorrectly retained an earlier-day
programme. All input timestamps, channel metadata and EPG data remain fixed.

An initial new-test import error was repaired before this negative execution;
that compilation failure is not treated as reproduction or a successful run.
Ignored local evidence: `build/epg-timezone-fixture-compile.log`,
`build/epg-timezone-before.log`, `build/epg-timezone-before.xml`.

## Minimal fix and ownership

`rememberEpgTimeZone` owns the UI-only device calendar policy. One application-
context receiver observes only `ACTION_TIMEZONE_CHANGED` while the guide's
lifecycle is STARTED. Each start rereads the device zone, so missed background
events cannot leave stale state. Stop and composition disposal unregister it.
There is no polling, permanent scope, retained Activity, new permission,
network request, parser mutation or per-row observer.

The receiver never trusts the intent's timezone extra; it rereads the actual
device setting. Android documents this as a protected system action. The
existing AndroidX compatibility registration supports the app's API-24 floor.
The exported system-broadcast flag does not introduce an app command endpoint.
See [Intent.ACTION_TIMEZONE_CHANGED](https://developer.android.com/reference/android/content/Intent#ACTION_TIMEZONE_CHANGED)
and [Android broadcast lifecycle guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts).

## Verification

The first fixed run passed 16 focused EPG/message/list/channel-refresh tests,
plus lintDebug/detekt in 2m. Six final host cases add ten stop/start cycles with
exact receiver-count assertions, disposal cleanup and ignored malformed extras.
The final focused run passed 18 cases, compiled two native regressions, and
passed lintDebug/detekt in 1m30s. These host results are not physical-device tests.

Two new instrumented cases exercise the actual Android UI after background /
resume and ten repeated cycles plus reopening. They alter only the test
process's Java default timezone and restore it afterward. The native fixture
uses UTC / Europe/Athens (the same two-hour winter offset), avoiding dependence
on recently renamed timezone identifiers in older API-24 system databases;
device settings,
user EPG files, playlists and network services are not modified. Compilation
does not prove their execution; current-head CI results must be checked.

The complete isolated local regression gate passed in 9m (227 tasks: 40
executed, 187 up-to-date): Debug 2651, Release / Play 2270 each, zero host
failures, errors or skips; unchanged Core 133 results reused. All 49 golden
comparisons are unchanged, with no added / changed goldens. Three diagnostic
background previews written under ignored build output are not baseline
updates. Lint has zero errors / warnings and one existing Hint per variant.
Detekt, debug / test APK assembly, and both app / harness benchmark Kotlin
variants passed. All twelve repository checks, eight performance-validator
cases and six runner contract / privacy cases pass.

The native fixture's conservative timezone-ID choice was made afterward,
without changing production / host-test inputs. The final incremental gate
passed in 38s (227 tasks: 10 executed, 217 up-to-date), compiling, linting and
packaging that exact fixture while retaining the same-source completed host /
golden results. All four Kotlin files are byte-identical between primary and
review checkouts. Current-head remote execution remains to be verified.
Goldens, failure checks, budgets and detekt baseline were not relaxed.

### Subsequent remote verification of b486d02

Push Android CI `37347016254` passed all six jobs. Raw native reports verify
144 / 147 / 147 passed methods on API 24 / 30 / 36, zero failures and six
assumption skips per API. Both added timezone methods passed on all three APIs.
PR CI `37347023580` also passed all six jobs after one API-24-only retry. Its
initial run assembled the APKs, then hung before instrumentation and produced
no API-24 runner report; this is not reclassified as an initial success.

The first performance attempt `37347023449` failed while downloading/installing
the API-35 SDK image (`Error on ZipFile unknown archive`), before benchmarks
could run. Its failed-job-only retry completed nine tests in 314.627s with no
test failures/errors/skips and complete final JSON. The independently checked
unchanged validator still rejects three frame CPU P95 budgets: channels
276.473ms, first player 437.503ms, fullscreen 414.726ms versus 100ms. Startup,
EPG and measured managed-heap limits pass. Successful measurement is not a
passing budget gate. Further
setup evidence and the runner's bounded-diagnostics correction are documented
in `docs/PLAYLIST_SELECTION_AND_RUNNER_STABILIZATION_2026-10-05.md`.

## Remaining release boundaries

Parent 8301f71 Android CI passed all six jobs; these are parent results, not a
substitute for the new code. Its complete performance evidence still exceeds
three frame CPU P95 budgets (channels 131.211ms, first player 416.932ms,
fullscreen 367.409ms vs 100ms). The timezone correction is not claimed to fix
those rendering stalls. Details: `docs/ENTRY_STAGGER_STABILIZATION_2026-10-05.md`.

ADB lists no connected physical phone or Mi TV. Current real-TV/provider
acceptance remains open. Keep the existing PR draft; no merge, release,
version bump or computer shutdown in this pass.
