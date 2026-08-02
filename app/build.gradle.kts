import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.roborazzi)
}

detekt {
    // Default rule set plus a handful of Compose-awareness overrides (see the config file) - not
    // a hand-tuned ruleset. The baseline is what keeps this from blocking on pre-existing code;
    // only new violations introduced from here on fail CI.
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

android {
    namespace = "com.uacastplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uacastplayer"
        minSdk = 24
        targetSdk = 36
        // CI overrides these via -Puacast.versionCode/-Puacast.versionName; the defaults below
        // are what local (non-CI) builds get.
        versionCode = (project.findProperty("uacast.versionCode") as String?)?.toInt() ?: 3
        versionName = (project.findProperty("uacast.versionName") as String?) ?: "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 32-bit x86 is dropped outright rather than merely split off: no shipping Android
            // device uses it, and every current emulator image is x86_64. It was 6.2MB of the
            // release APK's 22.6MB of native code (FFmpeg via nextlib, see libs.nextlib.media3ext)
            // for an audience of nobody.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // Native code is 78% of the release APK (22.6MB of 29.1MB), all of it the same FFmpeg decoders
    // built four times over. A per-ABI APK carries one of them: ~11.7MB for arm64-v8a against
    // 29.1MB before, i.e. what a phone actually downloads drops by well over half.
    //
    // universalApk stays on because this app is realistically sideloaded as often as it is
    // installed from a store, and a universal APK is the only one that can be handed to someone
    // without first asking what CPU their phone has. Publishing to Play Store would not need any
    // of this - `bundleRelease` produces an .aab and Play does the same split server-side - but
    // the bundle path cannot serve a direct download.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("UACAST_STORE_FILE")
                ?: (project.findProperty("UACAST_STORE_FILE") as String?)
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("UACAST_STORE_PASSWORD")
                    ?: (project.findProperty("UACAST_STORE_PASSWORD") as String?)
                keyAlias = System.getenv("UACAST_KEY_ALIAS")
                    ?: (project.findProperty("UACAST_KEY_ALIAS") as String?)
                keyPassword = System.getenv("UACAST_KEY_PASSWORD")
                    ?: (project.findProperty("UACAST_KEY_PASSWORD") as String?)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        // minSdk is 24, but the app uses java.time (EPG day schedules, the guide sheet's clock
        // formatting, the backup filename's date) which the platform only ships from API 26. On
        // Android 7.0/7.1 those calls were a NoClassDefFoundError at runtime - a crash on opening
        // the EPG guide, not a warning. Desugaring backports them into the APK instead.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // The one lint error in this project that is asking for the wrong thing. It fires because
        // the manifest declares `android.software.leanback` (required=false) without a
        // LEANBACK_LAUNCHER category - but that omission is the deliberate decision documented in
        // docs/TV_SUPPORT.md and in the manifest itself: the UI has no D-pad focus navigation, so
        // appearing on a TV launcher would strand the user with no way to move between channels.
        // Adding the category to satisfy lint would ship exactly the broken experience the comment
        // exists to prevent, so the rule is off rather than obeyed or baselined.
        disable += "MissingLeanbackLauncher"

        // CI runs lintDebug and treats it as a gate (see .github/workflows/android-ci.yml), which
        // only means anything if a *new* error fails the build rather than joining a growing pile.
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Required by Robolectric/Roborazzi: the screenshot tests render real Compose UI, which
            // needs the merged resources and the AAPT-generated table, not stubs. Flipping this on
            // affects every unit test, so it landed in its own commit with the full suite re-run.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Every APK of one release shares a versionCode by default, which a store rejects outright when
// several per-ABI APKs are uploaded together (see the splits block above) - it has no way to tell
// which one to serve an upgrading device. Multiplying the base code by ten and adding a per-ABI
// digit keeps ordering intact within an ABI and, since the offsets ascend with how "capable" the
// ABI is, makes a 64-bit device prefer the 64-bit APK when it could install either.
// The universal APK carries no ABI filter and so keeps the plain base code, below all of them.
private val abiVersionCodeOffsets = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86_64" to 3,
)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val offset = abiVersionCodeOffsets[abi] ?: return@forEach
            val base = output.versionCode.orNull ?: return@forEach
            output.versionCode.set(base * 10 + offset)
        }
    }
}

// Generated by :baselineprofile's BaselineProfileGenerator into src/main/baseline-prof.txt,
// replacing the hand-authored stopgap described in the README's "Stack" section - see
// docs/RELEASING.md for how/when to regenerate. automaticGenerationDuringBuild stays off so this
// never runs as a side effect of an ordinary build; it's invoked explicitly.
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.cast)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.nextlib.media3ext)

    implementation(libs.play.services.cast.framework)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.kotlinx.coroutines.android)

    baselineProfile(project(":baselineprofile"))

    // Debug-only: instruments Activity/Fragment/ViewModel and reports retained instances. Kept out
    // of release entirely (debugImplementation). See block 1.0 of the leak-fix plan - this is what
    // makes a leaked PlayerViewModel/ExoPlayer visible the day it appears instead of via an OOM.
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    // Instrumentation smoke test only - not run in CI (no emulator available there), but
    // :app:assembleDebugAndroidTest keeps it compiling. See MainActivitySmokeTest.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Screenshot tests (see docs/SCREENSHOT_TESTING.md). compose-ui-test-junit4 is needed on the
    // unit-test classpath too, not just androidTest: Roborazzi drives the same createComposeRule.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}

/**
 * Robolectric downloads the `android-all` runtime jar it executes against over its own HTTP client
 * at test time, and in this environment that fetch dies with an SSLHandshakeException ("unable to
 * find valid certification path") while Gradle resolves the very same artifact from the very same
 * host without complaint - so the problem is Robolectric's fetcher and its trust store, not
 * reachability. That single fact is what kept screenshot testing shelved; see
 * docs/SCREENSHOT_TESTING.md for the full diagnosis.
 *
 * The fix is to let Gradle do the fetching and put Robolectric in offline mode against the result.
 * No copy step is needed: Gradle's cache already stores the artifact alone in a per-hash directory
 * under exactly the `android-all-instrumented-<version>.jar` name Robolectric's offline resolver
 * looks for, so pointing `robolectric.dependency.dir` at that directory is enough - which also
 * avoids duplicating a 203MB jar into the build directory on every checkout.
 */
val robolectricSdk: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    robolectricSdk(libs.robolectric.android.all.instrumented)
}

/**
 * Compose-rule tests can only run against the debug variant, so the release unit-test task skips
 * them.
 *
 * `createComposeRule()` launches `androidx.activity.ComponentActivity`, declared solely by the
 * `compose-ui-test-manifest` artifact - a `debugImplementation` dependency, since it exists to host
 * tests and has no business in a release build. Its manifest entry is merged into the debug manifest
 * only, so under `testReleaseUnitTest` these fail at rule setup with "Unable to resolve activity for
 * Intent ... androidx.activity.ComponentActivity", which says nothing about variants and reads like
 * a broken test. CI runs `verifyRoborazziDebug` (the whole debug suite plus golden verification), so
 * nothing goes uncovered by excluding them here.
 *
 * By category rather than class-name pattern so renaming or moving a test cannot quietly drop it
 * back into the release run - see [com.uacastplayer.testing.RequiresComposeTestManifest].
 */
private val composeTestManifestCategory = "com.uacastplayer.testing.RequiresComposeTestManifest"

tasks.withType<Test>().configureEach {
    // Matched by name inside this lazy block rather than tasks.named("testReleaseUnitTest"): AGP
    // registers the per-variant test tasks itself, and they do not exist yet while this file is
    // being evaluated.
    if (name == "testReleaseUnitTest") {
        useJUnit { excludeCategories(composeTestManifestCategory) }
    }
    // Resolved lazily through a provider so the 203MB artifact is only fetched when tests actually
    // run, and so the wiring survives the configuration cache.
    val sdkDirectory = robolectricSdk.elements.map { it.first().asFile.parentFile.absolutePath }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Drobolectric.offline=true",
                "-Drobolectric.dependency.dir=${sdkDirectory.get()}",
                // Roborazzi renders real pixels rather than Robolectric's default no-op canvas;
                // without NATIVE graphics every golden image comes out blank.
                "-Drobolectric.graphicsMode=NATIVE",
            )
        }
    )
}



