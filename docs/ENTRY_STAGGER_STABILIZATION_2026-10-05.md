# List entrance state stabilization — 5 October 2026

## Confirmed UI/accessibility defect (P2)

EntryStagger kept completion inside animatedEntry, which leaves composition
when the motion policy becomes false. The outer alreadyPlayed memo remained
false for the mounted row. Re-enabling motion therefore created another
Animatable/layer, fading an already-visible row in again. A row first displayed
with motion disabled was not recorded in the container's played registry either;
after recycling it could animate when motion became enabled.

The actual execution path is GroupsOverview / SingleGroupChannelList:
animationsAllowed observes Android's animator scale, and passes the reactive
Boolean to each bounded staggeredEntry. No settings-source refactor is required.

| Trigger | Old behavior | Required behavior |
| --- | --- | --- |
| Disable/re-enable while entering | The animated branch is recreated | Keep the now-visible row static |
| Toggle after a completed entrance | Completion was disposed; a new layer appears | Do not replay completed content |
| Start with motion disabled, then enable | Existing row starts fading in | Preserve visible content |
| Recycle a statically displayed row | Registry does not recognize it | Preserve container-level seen state |
| New list epoch while motion is enabled | A new entrance is allowed | Retain this existing behavior |

## Reproduction and minimal fix

The first unchanged-production run executed eleven EntryStagger tests: six
failed, including the previously passing interruption test after its fixture
was strengthened. The fixture now asserts a real initial entrance, flushes
Snapshot notifications/Main Looper work, and advances only 64ms (below the
unchanged 220ms duration). The strengthened Android-setting case separately
also failed before the fix at the expected visible-content assertion. It
records transient graphics-layer modifiers and includes a positive new-epoch check so
undelivered settings callbacks cannot produce a false pass.

Completion now belongs to the outer row, keyed on the same container/key.
Disabling motion makes that row complete and records the statically displayed
key. Animation completion reports to this retained state. The animated branch
can be disposed without losing completion; new list epochs still animate.
The existing ten-item cap, 30ms stagger, 220ms duration, curve and lazy-item keys
are unchanged. No new observer, dependency, player command or network request
is introduced. Bookkeeping does not add an animation clock to static rows.

Six new regression cases cover completed toggles (ten cycles), initial static
content, lazy-child recycling, the production Android observer path under Robolectric, a new
epoch and native-child retention. The existing interrupted-entry case is
strengthened rather than suppressed. All 31 focused EntryStagger/OpenTransform/
PlayerHost/EPG cases pass after the fix, together with lintDebug/detekt in 2m2s.
The generic AndroidView retention case does not certify IPTV decoding.

The complete isolated regression gate passed in 8m51s (227 tasks: 30 executed,
197 up-to-date): Debug 2645, Release/Play 2270 each, zero failures/errors/skips;
49 unchanged golden comparisons, lint/detekt, debug/test APKs and both app/
harness benchmark Kotlin variants pass. Lint has zero errors/warnings (one
existing Hint per variant). Unchanged Core 133 results were reused. All twelve
repository checks, eight performance-validator cases and six runner contract/
privacy cases pass. The three Kotlin files are byte-identical between primary
and review checkouts. No negative run is called successful, no detekt suppression
is added and no screenshot baseline is regenerated.

## Release and measurement boundaries

Parent 853f4d2 passed all six Android CI jobs in both push and PR runs. The PR's
initial SDK-archive setup failure was preserved; one failed-jobs-only retry
passed without source/SDK/assertion/budget changes. Native artifacts distinguish
142/145/145 passes on API 24/30/36 and six opt-in assumption skips on each API.
Those older CI results do not certify this new EntryStagger modification.

The complete parent performance artifact contains nine tests in 311.265s and
all eight measured journeys, but the unchanged validator fails three frame
CPU P95 limits: channels 164.146ms, first player 437.024ms, fullscreen 396.226ms
versus 100ms. The benchmark does not toggle motion policy; this UI correction
is not claimed to fix those measured stalls or prove a percentage improvement.
Selected traces show overlapping main-thread rendering waits and RenderThread
shader work, not a specific production root cause. Budgets remain unchanged.

Detailed parent evidence: `docs/EPG_METADATA_REFRESH_STABILIZATION_2026-10-05.md`.
Current physical Mi TV/provider acceptance remains unavailable via ADB. Keep
the PR draft; no merge, release, version bump or computer shutdown in this pass.
