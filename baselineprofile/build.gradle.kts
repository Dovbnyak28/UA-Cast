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

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
