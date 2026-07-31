# Screenshot testing (Roborazzi) - blocked, with the actual reason

Not set up. This records what was measured, so the next attempt doesn't re-derive it.

## What is not the problem

Dependency resolution. All of these resolve from the repo's existing `google()` + `mavenCentral()`
setup, verified against `debugUnitTestRuntimeClasspath`:

- `io.github.takahirom.roborazzi:roborazzi:1.32.2`
- `io.github.takahirom.roborazzi:roborazzi-compose:1.32.2`
- `org.robolectric:robolectric:4.13` through `4.16`

An earlier note recorded this task as blocked by a "TLS fetch failure" at the dependency level.
That was wrong - see `gradle/wrapper/gradle-wrapper.properties` for the same misdiagnosis on the
Coil migration.

## What is the problem

Two things, in order of how they surface:

**1. Robolectric must be new enough for this app's `targetSdk`.** 4.13 refuses outright:

```
java.lang.IllegalArgumentException: failed to configure ...
    Package targetSdkVersion=36 > maxSdkVersion=34
```

4.16 gets past this.

**2. Robolectric downloads its own `android-all` runtime JAR, over its own HTTP client, at test
execution time** - not through Gradle. In this environment that fetch fails:

```
java.lang.AssertionError at MavenArtifactFetcher.java:129
  Caused by: java.util.concurrent.ExecutionException
    Caused by: javax.net.ssl.SSLHandshakeException
      Caused by: sun.security.validator.ValidatorException
        Caused by: sun.security.provider.certpath.SunCertPathBuilderException
                   (unable to find valid certification path to requested target)
```

Gradle resolves fine against the same hosts, so this is specific to Robolectric's fetcher and its
trust store - not to network reachability.

## What a next attempt needs

- Robolectric 4.16 or newer (for `targetSdk = 36`).
- The `android-all-instrumented` JAR for the chosen SDK present locally, with Robolectric pointed
  at it rather than left to fetch: `robolectric.offline=true` and `robolectric.dependency.dir`, or
  a `robolectric.properties` pinning an SDK whose JAR is already cached. Getting that JAR onto the
  machine is the whole remaining task.
- `testOptions.unitTests.isIncludeAndroidResources = true` in `app/build.gradle.kts` - currently
  `false`. Flipping it affects all 784 existing unit tests, so change it in its own commit and
  re-run them.

## Whether it is worth it

Lower value here than it looks. The design system already has `@Preview` coverage across its
components (see `docs/DESIGN_SYSTEM.md`), which catches the same class of regression at authoring
time; screenshot tests would add CI enforcement of it, not new information. Weigh that against
owning golden images for four locales and two themes.
