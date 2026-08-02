package com.uacastplayer.testing

/**
 * Marks a unit test that can only run against the **debug** variant.
 *
 * `createComposeRule()` launches `androidx.activity.ComponentActivity`, and the only thing that
 * declares that activity is the `compose-ui-test-manifest` artifact - which is a
 * `debugImplementation` dependency, because it exists purely to host tests and has no business in a
 * release build. Its manifest entry is therefore merged into the debug manifest and only that one,
 * so under `testReleaseUnitTest` these tests fail at rule setup with:
 *
 * ```
 * java.lang.RuntimeException: Unable to resolve activity for Intent { ...
 *     cmp=com.uacastplayer/androidx.activity.ComponentActivity }
 * ```
 *
 * which says nothing about variants and reads like a broken test. `app/build.gradle.kts` excludes
 * this category from the release unit-test task; see the comment there.
 *
 * A marker interface used as a JUnit `@Category`, rather than excluding by class-name pattern, so
 * that moving or renaming a test cannot silently drop it back into the release run - and so the
 * reason travels with the test instead of living only in the build file.
 */
interface RequiresComposeTestManifest
