# Playlist selection and test-runner stabilization — 5 October 2026

## Confirmed UI defect (P2)

`PlaylistSourceSheet` rendered the active radio visually, but its clickable row
exposed only `Role.RadioButton`, not the `Selected` semantics property. The child
`RadioButton(onClick = null)` did not supply that state to the accessible row.
TalkBack therefore could not distinguish the active playlist by its selection
state. This was an accessibility inconsistency, not a failed playlist import.

Execution path: Home's playlist-source card → source sheet → `activeId` → row's
`isActive` → visual radio/background, but no corresponding selection semantics.
The saved source ID and click callback were correct.

Six actual Compose tests ran before the production change: three failed on the
missing `Selected` assertion, after showing the expected source; three controls
passed, including removal cancellation and access to historical over-capacity
sources. Ignored evidence: `build/playlist-selection-before.log` and
`build/playlist-selection-before.xml`. This is not a speculative TalkBack warning.

### Minimal change

Use the existing `isActive` in `Modifier.selectable` and mark the list as a
selection group. No new state owner, coroutine, listener, import path, source
persistence change or player lifecycle change is introduced. The nested delete
button remains a separate action. Focus, sizes, colors and animation policy
are unchanged.

The first fixed focused run passed ten cases. The final focused run passed
sixteen, including seven source-sheet cases, five TV navigation cases, first-run
guidance and URL validation. Controls exercise a source switch with exactly one
callback, externally changed/absent active ID, cancellation and confirmed removal,
over-capacity scrolling, and TV OK with focus retained. Two new native cases use
synthetic sources only and do not modify saved phone data. They compile locally;
execution of the new cases must be verified in current-head CI.

One invalid member-function import in the added fixture was corrected before
the final focused execution; that compilation error is not counted as a bug in
production or a successful regression test.

## Confirmed test-infrastructure defects (P2)

The prior `run-instrumented-tests.sh` left every ADB operation unbounded. Its
report directory and failure handler were initialized only after installation
and display setup. A hang before instrumentation therefore reached the outer
CI deadline with no per-phase evidence. Also, discovery inside a process
substitution masked ADB's exit status, reporting a transport failure as an
empty authorized-device list.

Concrete incident: b486d02's PR API-24 job completed APK assembly at 17:20:45 UTC,
then emitted no `Running ...` marker or runner artifact before the action timed
out at 17:45:22. This localizes the stall to the post-build/pre-instrumentation
interval, but does not prove which ADB operation or emulator subsystem hung.
The same SHA passed all six push-CI jobs. One API-24-only retry also passed; no
assertion, emulator setting or job timeout was relaxed.

Mock reproduction replaced only ADB and Gradle, never contacting a device.
With an installation that never returned, the old helper exceeded an external
watchdog and left no failure report. A separate seeded-report test proved that
old crash evidence could survive a subsequent passing run. A setup failure
could also retain an old passing `runner.txt` in a reused local checkout.
Ignored negative logs: `build/instrumented-timeout-before.log` and
`build/instrumented-stale-report-before.log`.

### Bounded commands and trustworthy evidence

Initialize/reset only the helper's generated reports before device discovery.
Bound ordinary ADB commands at 30s, each installation at 180s and instrumentation
at 900s, with 5s termination grace. These limits are explicit positive integer
overrides, not retries or a substitute for CI's outer deadline. GNU `timeout`
is required; the preserved Windows physical-device helper is unchanged.

Record fixed phase names, not command arguments or device content, in
`setup.txt`. Preserve partial raw per-method output in `runner.txt`. Propagate
discovery/runner exit codes. A timeout remains a failure, and the existing
`FAILURES!!!`, absent summary, transport and report-write checks remain intact.
Crash diagnostics are bounded and eligible only after positively identifying
an emulator. A physical handset's crash buffer is never captured. No app data,
saved sources, credentials, lock settings or production package is removed.

Twelve mock contracts cover normal/raw summaries, a crashed runner, contradictory
failure/OK output, transport failure, physical-device privacy, hung install,
hung runner with partial output, hung discovery, discovery error propagation,
and stale reports after success/setup failure. All pass in the isolated review
checkout. Bash syntax validation passes too.

## Full local gate

The complete isolated gate passed in 7m49s (227 tasks: 34 executed, 193 up-to-date).
Debug: 2656; Release / Play: 2270 each; zero host failures, errors or skips.
Unchanged Core 133 results were reused. All 49 golden comparisons remain
unchanged, with zero added/changed goldens. Three generated diagnostic
background previews under ignored build output are not baseline updates.
Lint has zero errors/warnings and one existing Hint per variant. Detekt, debug /
test APK assembly, both app benchmark Kotlin variants and both harness variants
passed. Twelve repository checks, eight performance-validator cases and twelve
runner contracts pass. Ignored full log: `build/playlist-selection-complete-gate.log`.
No golden, budget, detekt baseline, locale, key or playlist was changed.

## Verified parent CI, not new-head certification

b486d02 push run [37347016254](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37347016254)
passed all six jobs. Native per-method results are API 24/30/36: 144/147/147
passes, zero failures and six assumption skips per API. Both EPG timezone
methods passed on all three. Aggregate `OK (150/153/153 tests)` includes skips;
anonymous runner status entries are not added to the passed-method count.

PR run [37347023580](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37347023580)
passed all six jobs after the single failed-job-only retry. Its initial API-24
hang remains recorded above, not rewritten as an initial pass.

The first b486d02 performance attempt failed while installing the Android-35
system-image archive (`Error on ZipFile unknown archive`), before any benchmark
ran. The failed-job-only retry completed all nine benchmark tests in 314.627s,
zero failures/errors/skips, including sleeping-display readiness and all eight
measured journeys. The final JSON and XML were downloaded and independently
checked with the unchanged validator. It still fails three frame CPU P95 rules:
channels 276.473ms, first player 437.503ms, fullscreen 414.726ms versus 100ms.
EPG guide 60.916ms, cold/warm startup 1769.801/828.392ms, 40k restore 1774.188ms,
350k EPG parse/index 2362.834ms and worst EPG managed-heap metric 96176 KiB meet
their limits. This is complete measurement, not a passing budget gate, a process
RSS measurement or certification of a 128MB physical device.

Selected iteration-zero traces show overlapping main-thread rendering waits and
RenderThread work: postAndWait maxima for channels / first player / fullscreen
are 294.729/256.831/295.299ms; matching shader_compile maxima are
90.089/81.355/132.585ms. These wall times must not be summed as CPU cost or
interpreted as a unique production root cause. No controlled A/B or percentage
improvement is inferred from hosted-runner differences. Evidence is under
ignored `build/perfb486`; short copied traces in `build` are
`perfb486-channels0.perfetto-trace`,
`perfb486-first0.perfetto-trace`, `perfb486-fullscreen0.perfetto-trace`.

## Remaining boundaries

The latest complete parent performance run (b486d02) still exceeds three
frame CPU P95 budgets, as recorded above. No improvement to these is attributed to this semantic
or test-tool correction. Current physical phone/Mi TV ADB inventory is empty;
real-TV/provider acceptance and manual TalkBack usability remain open.

Keep PR #4 draft. No merge, release, version bump or computer shutdown.
