# Player opening motion stabilization — 5 October 2026

## Confirmed defect and narrow change

`ui/components/OpenTransform.kt` retained its `graphicsLayer` after the entrance
animation finished. It also lacked the static path used by the application's
other decorative effects when Android's animator scale is zero. The six initial
host tests reproduced five failed assertions on the old implementation; the
temporary-layer assertion already passed.

The modifier now follows the existing reactive motion policy. Disabled and
completed openings return the caller's original modifier, without an Animatable
or animation layer. Disabling motion during opening makes the content static;
re-enabling it does not hide already-visible content. A new opening key may still
animate. The animation curve, duration, scale and anchor are unchanged.

This is not a claim that every Android version animated with scale zero: the
framework may itself snap an animation. The proven defects are the missing
explicit static path and retained layer, not a new player-state failure.

Media3 ownership, release, playback commands, ViewModel state and Cast/DLNA code
are untouched. Removing a modifier layer must not dispose its still-mounted
native child; two additional host regressions assert one View factory invocation
and no release on completion/interruption. These are AndroidView/Compose host
tests, not proof of long-duration decoding on a physical TV.

## Verification

The nine motion cases passed with detekt before adding the two native-child host
regressions. They cover static startup, preserving the caller's modifier,
temporary/completed layers, unrelated recomposition, a new opening request and
system motion-policy changes. Robolectric notifications use the Activity's
resolver and queued Main Looper work. The new-key case records its temporary
layer and verifies its settled state instead of assuming one paused-clock frame
has already received an external state change. Assertions were not removed.

The final complete isolated application gate passed in 9m7s: Debug 2630,
Release/Play 2270 each, zero failures/errors/skips. All eleven motion cases and
both new fixture cases pass. The unchanged Core results (133 tests) were reused
because Core sources/dependencies are untouched. All 49 existing screenshot
goldens verified; no golden images have been regenerated. Debug/Release/Play lint
has no errors/warnings, retaining one existing Hint per variant. App/Core detekt,
debug/test APK assembly and both application/harness benchmark-variant Kotlin
compilations passed.

The first complete attempt passed Debug 2628 and Release/Play 2268 each, but
stopped at a Compose lint error in the new test helper. The helper is now a
Modifier extension; no lint suppression or baseline was added. A second Debug
run failed the existing row-resolution cache barrier test because its awaited
resolution had already ended; this is not evidence of deletion racing an active
writer or of a motion-to-cache dependency.

Review found a separate, confirmed fixture resource defect: HeldIconServer did
not retain or close accepted client sockets. A new raw-socket regression failed
on the old fixture with SocketTimeoutException after server.close(), proving
that closing the fixture left its connection open. The fixture now owns all
accepted sockets, protects accept/close races, closes clients and joins its
worker. An additional GC-pressure case checks that a held request stays open
until explicit close. Existing cache-barrier assertions and production icon
code are unchanged. This fixes the reproduced ownership/cleanup defect; it is
not asserted to prove GC was the unique cause of the earlier intermittent
barrier assertion.

After the fixture fix, the focused icon/motion classes passed 23/23 with lint and
detekt (2m27s task execution), then the complete gate above passed. No previous
failed run is counted as green or removed from the verification history. The
remote results for that source are recorded separately below; preceding b3df89c
results are not substituted for that head.

All eleven repository architecture/privacy/Premium checks, six instrumentation
runner contract/privacy cases and eight performance-validator cases passed.

## Follow-up: retain entrance ownership while collapsed

An integration regression using the actual PlayerHost reproduced a separate UX
defect: after a completed opening, collapsing and expanding the same host added
an entrance layer again (expected zero, actual one). The modifier was created
inside the expanded branch; that branch leaves composition on collapse, so its
remembered completion state was lost despite an unchanged key.

PlayerHost now creates the opening modifier before its child conditional and
applies it only to PlayerScreen. The mini bar is not scaled/faded. The same key,
duration and curve remain; a fully disposed/reopened host and a new key can still
animate. Playback/lifecycle callbacks, Media3 creation/release, request startup,
Cast and DLNA ownership are unchanged.

Four real-host Compose regressions cover ten collapse/expand cycles (same
ExoPlayer and one live ViewModel), collapse during entrance, full close/reopen,
and a new key. They use empty requests to avoid unrelated network/decoder work;
they do not prove stream decoding or physical-TV rendering. Paused-clock state
changes explicitly flush Snapshot notifications and the Main Looper before
advancing 64ms, less than the 220ms entrance. This prevents stale test state from
being mistaken for a missing transition. The first single regression failed
before the production change and passed afterwards; all four plus the eleven
modifier cases pass. The complete isolated gate for this hoist passed in 9m9s:
Debug 2634, Release/Play 2270 each, zero failures/errors/skips, unchanged Core
133 results reused, all 49 unchanged goldens, lint/detekt, debug/test APKs and
both application/harness benchmark Kotlin variants. The new test's initially
overlong line was fixed; no detekt suppression was added. Later EPG metadata
regressions and verification are documented separately in
`docs/EPG_METADATA_REFRESH_STABILIZATION_2026-10-05.md`.

The combined final gate with those five EPG regressions succeeded in 10m16s:
Debug 2639, Release/Play 2270 each, zero failures/errors/skips; 49 unchanged
goldens, lint/detekt, APK assembly and app/harness benchmark Kotlin compilation
pass. The completed same-source Debug/golden phase and unchanged Core 133
results were reused. The preceding interrupted attempt was not labeled green;
Release/Play completed in the resumed gate. Twelve repository checks, eight
performance-validator cases and six runner contract/privacy cases also pass.

## Latest completed remote evidence: 21c3be3

[Android CI passed all six jobs](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37244290221),
including native instrumentation on API 24, 30 and 36, unit/screenshots, quality
checks and unsigned packaging. This head contains the preceding modifier/socket
fixture fixes, not the subsequent PlayerHost hoist.

The [performance run](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37244290218)
is incomplete, not green: ADB instrumentation ended with EOF and UTP reported
`device 'emulator-5554' not found`. The artifact contains three completed XML
records (cold/warm startup and channel opening), not all nine requested tests,
and no final benchmark JSON. Independently running the unchanged validator
rejects it as incomplete. No crash root cause in production code is inferred
from this transport failure. Partial text/trace values do not replace complete
JSON evidence; no timing improvement is claimed. The three previously measured
frame-budget exceedances remain open pending a complete new measurement.

## Completed CI before this production change

For preceding head `b3df89c`, [Android CI passed all six jobs](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37238731426).
The [API-35 performance execution](https://github.com/Dovbnyak28/UA-Cast/actions/runs/37238731429)
completed nine tests in 257.36s with no failures, errors or skips: the same eight
measurement journeys plus sleeping-display readiness. Downloaded XML/JSON were
checked independently. The unchanged validator still rejects three measured
frame limits:

| Journey / metric at b3df89c | Result | Limit |
| --- | ---: | ---: |
| Open channels frame CPU P95 | 161.814ms | 100ms |
| First player frame CPU P95 | 234.486ms | 100ms |
| Fullscreen frame CPU P95 | 310.594ms | 100ms |
| EPG guide frame CPU P95 | 53.348ms | 100ms |
| Cold / warm startup median | 1120.270 / 234.758ms | 5000 / 2500ms |
| 40k-channel restore median | 1144.174ms | 10000ms |
| 350k EPG parse/index median | 1586.460ms | 60000ms |
| Worst EPG managed-heap metric | 124556 KiB | 262144 KiB |

These are measurements of b3df89c, not of the new motion change. The budget gate
is red despite its test execution being successful. No budget, required journey,
minimum iteration count or timeout is relaxed.

## Measurement and device limitations

A same-host two-journey before/after run was prepared with the old APK and
unchanged harness retained privately under ignored build output. This time the
owned local emulator cannot boot: WHPX reports `Failed to setup partition,
hr=80070005`, followed by failure to initialize WHPX. ADB has no ready device.
The guarded measurement script therefore refuses to install/run. No A/B samples
were collected and no timing gain is claimed. Windows hypervisor settings are
not changed to work around this infrastructure failure.

A current b3df89c fullscreen trace contains 202.104ms main-thread postAndWait,
205.986ms RenderThread drawing, 135.864ms flush commands and 88.366ms shader compile.
It also contains a 161.048ms binder transaction inside Compose:onRemembered.
These are observations from one software-GPU trace, not proof of a specific
settings observer or playback bug. They do not justify speculative player-state
changes or relaxed frame budgets.

The physical Mi TV is not presently ADB-accessible. Current TV UI/decoder/provider
acceptance remains unverified; older successful tests are not substituted for a
new APK run. Previous unreproduced API-24 crash/hang root causes also remain
unverified. The PR remains draft; no release, merge, version bump or computer
shutdown is performed by this pass.
