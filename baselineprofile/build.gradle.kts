import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.uacastplayer.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // BaselineProfileRule.collect requires API 28+ to actually produce a profile (older
        // devices are skipped by the rule itself, not a hard failure) - unrelated to :app's own
        // minSdk 24, which stays unchanged.
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Drives :app through cold start plus the critical journeys in BaselineProfileGenerator on a
// connected device (no Gradle-managed emulator configured in this project) and writes the result
// to app/src/main/baseline-prof.txt, replacing the hand-authored stopgap - see docs/RELEASING.md
// for when/how to regenerate. automaticGenerationDuringBuild stays off: this only runs when
// explicitly invoked (./gradlew :app:generateBaselineProfile), not on every release build.
baselineProfile {
    useConnectedDevices = true
}

// ...except that the comment above was only half true, and `./gradlew build --dry-run` says so:
//
//     assemble -> collectNonMinifiedReleaseBaselineProfile -> connectedNonMinifiedReleaseAndroidTest
//
// The plugin treats "assemble this module" as "produce a baseline profile", which means running
// the macrobenchmark on a connected device. Since the root `build` reaches every module's
// `build`, and `build` = `assemble` + `check`, a plain `./gradlew build` silently turned into a
// device-attended run of tens of minutes that fails outright with no phone plugged in.
//
// So `build` here is rebound to the work it is actually meant to cover - compile and package both
// variants, then `check` - and the lifecycle `assemble` is left exactly as the plugin wired it, so
// the profile is still generated on request (./gradlew :app:generateBaselineProfile, and see
// docs/RELEASING.md), just no longer by accident.
tasks.named("build") {
    setDependsOn(
        listOf(
            tasks.named("assembleBenchmarkRelease"),
            tasks.named("assembleNonMinifiedRelease"),
            tasks.named("check"),
        )
    )
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
