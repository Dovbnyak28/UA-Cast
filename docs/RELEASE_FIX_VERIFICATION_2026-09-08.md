# Release fix verification — 2026-09-08

Scope: close the four confirmed issue families from the repeated audit, preserve the
accumulated fixes, and verify the 0.9.3 GitHub candidate. This report supersedes the
open status of these four findings; it does not certify every receiver or Play service.

## Confirmed findings closed

| Finding | Correction | Regression evidence |
| --- | --- | --- |
| P1: importing a backup during favorites startup could replace existing favorites | Await the repository's initial load before calculating the merge | `StartupBoundaryRegressionTest`: delayed disk load, import, durable result includes both entries |
| P1: restoring a player could bypass parental restrictions | Explicit readiness, deferred opening, and filtering the complete restored navigation list | `StartupBoundaryRegressionTest`, `PlayerOpenReadinessTest`, `PlayerRestoreAccessTest`; device recreation with a real configured PIN and a locked neighboring channel |
| P2: Activity recreation could replace the media item of a retained player | Retain the UI request and reattach the same request without restarting the player | `PlayerRestorationRegressionInstrumentedTest`: same ViewModel and no playlist-change transition after recreation |
| P2: an old icon request could overwrite a newer positive cache result | Serialize cache publication and reject invalidated generations; a negative duplicate cannot replace a positive result | `IconCacheGenerationTest`: delayed failure, invalidation, successful duplicate, then stale completion |

The player request is intentionally identity-based: a new user tap is a new request,
even when its channel list and index match an earlier request. Closing playback clears
the attachment identity. Existing callers without a request retain explicit restart behavior.

## Verification performed

- App debug JVM tests: **2,061 passed**, zero failures/errors/skips.
- Core JVM tests: **114 passed**, freshly executed with `--rerun-tasks`.
- Release JVM tests: **1,846 passed**; Play JVM tests: **1,846 passed** on the
  complete distribution rerun (see the host transport caveat below).
- Roborazzi verification passed; no screenshot goldens were regenerated for these fixes.
- Android 16 isolated emulator, targeted player suite: **16 passed**.
- Android 16 isolated emulator, full instrumentation suite: **81 passed**, two expected
  assumption skips for the opt-in private-playlist fixture, zero failures.
- App/core Detekt passed; baseline remains empty, with no new suppressions.
- Play lint: zero errors and zero warnings, with three informational hints retained.
- Signed release APK assembly and Play AAB packaging passed.
- All **15** `scripts/check-*.sh` guards passed, including dependency boundaries,
  benchmark fixture isolation, legal assets, Play distribution and APK version ordering.
- `git diff --check` passed.

Two unchanged R8 resource-provider async-parsing warnings remain. Lint hints concern
target-SDK freshness and available library versions. These are not silently counted as
application test failures or claimed to have been eliminated.

### Host transport caveat discovered during extra stress testing

The first additional Play run hit a 5-second localhost socket timeout in the
1,000-segment proxy test. The isolated test and the full release/Play rerun passed,
but repeated Windows stress reproduced the timeout. During a captured failure the
proxy had zero active clients, no rejected requests, and idle serving workers; the
client had received zero bytes.

A separate plain-Java HTTP server with no application code reproduced a read timeout
on request 9,883, after accepting and completing all 9,883 requests. This demonstrates
that the symptom does not require the application, but does not identify which host
transport component caused it. No production workaround or relaxed test deadline was
introduced. The permanent test now reports received-byte counts and thread stacks on
timeout. Independent Linux CI is still required before publishing the release.

## Upgrade verification

The universal APK has application ID `com.uacastplayer`, version name **0.9.3** and
version code **124**. Its signer matches the public 0.9.2 APK (version code **114**).
The three ABI-specific APK codes are 121, 122 and 123.

On the isolated emulator, install the public 0.9.2 APK, select Spanish, and install the
new universal APK with `adb install -r`, without clearing or uninstalling the package:

- Installation succeeds and the package retains its original first-install timestamp.
- Version code increases from 114 to 124.
- The new Activity starts successfully and retains the selected Spanish language.
- The emulator crash buffer contains no crash entries.

This verifies update acceptance and one persisted preference, not exhaustive migration
of every possible user database. Physical phones were not used or modified in this run.

## Architecture after-check

- Existing repositories still own persistence; no duplicate store was introduced.
- The new `PlayerRequestViewModel` owns only UI navigation intent. It creates no player,
  network client, coroutine scope, singleton or Activity reference.
- The player ViewModel remains the engine owner; lifecycle recreation does not add a
  second owner or listener registration path.
- Restore authorization remains a pure policy and is tested independently of Compose.
- Icon network operations stay outside the cache lock; only short state operations
  are serialized.

## Boundaries of the verdict

### Additional defects exposed by the first Linux CI run

Run `34261141864` passed debug/screenshot, release/Play JVM, quality, packaging, API 30
and API 36 jobs, but correctly rejected the release on API 24 with four failures:

- **P1 production compatibility:** configuring the real parental PIN threw
  `NoSuchAlgorithmException`: the platform `PBKDF2WithHmacSHA256` factory requires
  API 26, while the application supports API 24. `PinHasher` now uses a fixed
  256-bit PBKDF2-HMAC-SHA256 fallback only when that factory is unavailable. The
  platform still provides the HMAC primitive; salt encoding, work factor, and saved
  hash format are unchanged. Five added core tests independently match OpenSSL
  vectors and the native Java factory, including UTF-8 and empty passwords.
- **Test harness compatibility:** three UI measurement fixtures called the API 26
  window PixelCopy path on API 24. They now use the older whole-screen automation
  capture on API 24/25; measurements still run, rather than skipping those tests.
- **Missing CI artifacts:** instrumentation only printed its output. The script
  now saves that output under the existing artifact path before inspecting either
  adb status or the JUnit outcome, including failures.

Local core verification after these changes: **119 passed**, zero failures, with
app/core Detekt and both debug APKs building successfully. The added device test
checks an independently derived stored PIN hash on every CI Android version.
These changes require a fresh full CI run; the rejected first run is not a release
approval. See the final GitHub Release for the successful run and published commit.

References: [Android SecretKeyFactory availability](https://developer.android.com/reference/javax/crypto/SecretKeyFactory.html),
[Mac availability](https://developer.android.com/reference/javax/crypto/Mac),
[PBKDF2 definition](https://www.rfc-editor.org/rfc/rfc8018#section-5.2),
[PixelCopy API](https://developer.android.com/reference/android/view/PixelCopy).

### Follow-up network regression verification

The second run, `34263315973`, confirmed that the PIN compatibility test and all
three UI captures pass on API 24. It exposed two separate failures:

- Release JVM: `ConcurrentModificationException` in the **DLNA test fixture**.
  `Collections.synchronizedList` protected appends, but not the concurrent `count`
  iterator used by the assertion. Both fixture journals now use snapshot iteration
  through `CopyOnWriteArrayList`. No production DLNA behavior was weakened.
- API 24: a connection reset while requesting a proxy manifest. Inspection found
  that the test origin assumed a complete HTTP header block in one socket read and
  advertised implicit keep-alive despite immediately closing each connection. It
  now consumes the full header block, declares `Connection: close`, and has a bounded
  read timeout. A new fixture test fragments headers into three-byte reads, and the
  manifest/segment test now performs **20** consecutive polls.

The same trace exposed a concrete production error-handling gap: an `IOException`
while reading a manifest body escaped before any receiver headers were sent. Two
new tests reproduced it (immediate failure and partial-body failure). Both failed
before the change, then passed after `ProxyResponseServing` converted this early
failure to HTTP 502 with no partial body, no false progress, and no provider detail.
This does not change error handling after streaming response headers have committed.

The targeted proxy/DLNA tests, Detekt and Android test compilation passed locally.
The final CI run must validate the complete updated candidate; neither rejected
run is presented as a passing release gate.

The third run, `34265294523`, passed debug tests, quality, packaging, and API 30/36,
but exposed a brittle release-JVM shutdown assertion in `CastProxySessionTest`.
It required the TCP handshake itself to fail immediately after `stop()`. Inspection
of JDK 21's `NioSocketImpl.close()` confirms that descriptor disposal can wait for
an in-progress `accept()` to unwind. The test now sends an HTTP request and requires
connection refusal/reset or EOF with zero response bytes. Its one-second read
deadline still fails on a hang, and any HTTP response also fails. No production
shutdown delay, retry, timeout increase, or blocking thread join was introduced.
The instrumentation launcher now streams runner output to CI and its report as it
arrives, preserving partial progress if a device hangs. Both adb and report-writer
exit codes are checked; JUnit failure and missing-summary checks remain mandatory.
The emulator step has a 30-minute cap within the existing 45-minute job budget so
partial reports can still upload on step timeout. The shutdown regression passed
20 separate local JVM runs; all four runner-status contract cases passed.

The four reproduced audit findings are fixed and covered by regressions. This is not
a claim that no unknown bugs exist. Real Hisense VIDAA/Chromecast interoperability,
Google Play purchase/restore flows, Play Console acceptance, and large-scale behavior
on a 128 MB-heap physical device still require their respective environments.

Device player tests use a synthetic origin to verify lifecycle/state behavior; they
do not prove every real stream codec decodes successfully. GitHub CI and publication
status must be read from the actual run/release, not inferred from local test success.
