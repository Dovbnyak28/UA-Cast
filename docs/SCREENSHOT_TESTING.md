# Screenshot testing (Roborazzi)

Working. `app/src/test/kotlin/com/uacastplayer/ui/screenshot/` holds the tests, and the golden
images live in `app/src/test/screenshots/` and are committed.

```bash
./gradlew :app:recordRoborazziDebug   # regenerate goldens after an intended visual change
./gradlew :app:verifyRoborazziDebug   # fail on any pixel diff
```

`verifyRoborazziDebug` runs the whole unit-test suite with verification switched on, so a golden
mismatch surfaces as an ordinary test failure (`DesignSystemScreenshotTest > emptyState_cinema
FAILED`). The diff and the actual render are written to `app/build/outputs/roborazzi/` as
`*_compare.png` / `*_actual.png` - that directory is gitignored.

## What was actually blocking this

Two things, neither of which was dependency resolution. An early note recorded this task as blocked
by a "TLS fetch failure" at the dependency level; that was wrong, the same way the Coil migration
was misdiagnosed.

**1. Robolectric must be new enough for this app's `targetSdk`.** 4.13 refuses outright:

```
java.lang.IllegalArgumentException: failed to configure ...
    Package targetSdkVersion=36 > maxSdkVersion=34
```

4.16 supports API 36 and is what the project now pins.

**2. Robolectric downloads its own `android-all` runtime JAR over its own HTTP client at test
execution time**, not through Gradle, and that fetch dies here:

```
java.lang.AssertionError at MavenArtifactFetcher.java:129
  Caused by: javax.net.ssl.SSLHandshakeException
    Caused by: sun.security.provider.certpath.SunCertPathBuilderException
               (unable to find valid certification path to requested target)
```

Gradle resolves the *same artifact from the same host* without complaint, so this is Robolectric's
fetcher and its trust store, not reachability.

### The fix

Let Gradle fetch it and put Robolectric in offline mode against the result. `app/build.gradle.kts`
declares a `robolectricSdk` configuration holding
`org.robolectric:android-all-instrumented:16-robolectric-13921718-i7`, and every `Test` task gets:

```
-Drobolectric.offline=true
-Drobolectric.dependency.dir=<the Gradle cache directory holding that jar>
-Drobolectric.graphicsMode=NATIVE
```

No copy step is needed. Gradle's cache stores the artifact alone in a per-hash directory under
exactly the `android-all-instrumented-<version>.jar` filename Robolectric's offline resolver looks
for, so the cache directory can be handed over as-is - which matters, because the jar is 203MB and
copying it into `app/build/` on every checkout is not free.

`graphicsMode=NATIVE` is not optional: without it Robolectric's canvas is a no-op and every golden
comes out blank.

### Where the version string comes from

`16-robolectric-13921718-i7` is not free-form. `DefaultSdkProvider` in robolectric 4.16 builds it as
`String.join("-", androidVersion, "robolectric", frameworkBuild, "i7")`, and for API 36 that is
androidVersion `16`, framework build `13921718`, instrumentation revision `i7`. **It moves only when
the `robolectric` version in `gradle/libs.versions.toml` moves.** To re-derive it after a bump,
disassemble the provider rather than guessing:

```bash
javap -p -c org/robolectric/plugins/DefaultSdkProvider.class          # API level -> framework build
javap -p -c 'org/robolectric/plugins/DefaultSdkProvider$DefaultSdk.class' | grep -B12 android-all-instrumented
```

Note API 36 requires **Java 21** (the provider records a required-JDK per SDK; API 35 and below want
17). This project's Gradle already runs on Temurin 21, so that is satisfied - but a machine on JDK
17 must pin an older `sdk` in `@Config` instead.

## Other setup this needed

- `testOptions.unitTests.isIncludeAndroidResources = true` in `app/build.gradle.kts`, previously
  `false`. Screenshot tests render real Compose UI and need the merged resource table, not stubs.
  This affects every unit test; the full suite (847) passes with it on.
- `compose-ui-test-junit4` and `compose-ui-test-manifest` on the **unit-test** classpath, not just
  androidTest - Roborazzi drives the same `createComposeRule`.
- `@Config(qualifiers = "w411dp-h891dp-xhdpi")` on the test class. Without a pinned surface,
  Robolectric picks its own screen size and density and any change to either rewrites every image.

## CI enforcement

Wired in. `.github/workflows/android-ci.yml` runs `:app:verifyRoborazziDebug` as its unit-test
step - the task *is* `testDebugUnitTest` with verification switched on, so it covers the whole debug
suite and the goldens in one pass rather than running the suite twice.

Two things had to change to make that possible:

- **CI moved from JDK 17 to 21.** Robolectric records a required Java version per Android API level
  and API 36 demands 21 (`DefaultSdkProvider`'s entry is `("16", "13921718", "REL", 21)`); on 17 every
  Robolectric test refuses to start. The app is still compiled to Java 17 bytecode - this changes
  what Gradle runs on, not what ships.
- **`testReleaseUnitTest` skips the Compose-rule tests.** `createComposeRule()` launches
  `androidx.activity.ComponentActivity`, declared only by `compose-ui-test-manifest`, which is a
  `debugImplementation` dependency - so its manifest entry exists in the debug merged manifest and
  nowhere else, and under the release variant these fail at rule setup with "Unable to resolve
  activity for Intent ... ComponentActivity". They are excluded by JUnit category
  (`RequiresComposeTestManifest`) rather than by class-name pattern, so moving or renaming a test
  cannot quietly drop it back into the release run. Debug runs 868 tests, release 864 - the
  difference is exactly those four.

**The goldens were recorded on Windows and the runner is Linux.** That is still unverified: the
first CI run is the experiment. Robolectric renders through the Skia and fonts bundled in the
`android-all` jar - the same artifact everywhere, which is the reason to expect a match - but
cross-platform antialiasing is a known failure mode for screenshot testing generally. If the step
fails on a diff rather than a real regression, the workflow uploads `app/build/outputs/roborazzi`
as the `roborazzi-screenshot-diffs` artifact (`*_compare.png` side-by-side, `*_actual.png` as
rendered); compare, then re-record on the runner and this becomes a "regenerate on CI" workflow
instead of a "regenerate locally" one.

## Whether it is worth it

Modest. The design system already has `@Preview` coverage across its components (see
`docs/DESIGN_SYSTEM.md`), which catches the same class of regression at authoring time. What these
add is *enforcement* - a `@Preview` only helps whoever opens it, whereas a golden fails the build.
Weigh that against owning golden images as the component set and themes grow.
