pluginManagement {
    // AGP 8.13.2 bundles R8 8.13.19, which cannot parse Kotlin 2.4 metadata. The app and several
    // current dependencies use Kotlin 2.4, whose documented minimum is R8 9.1.29. Keep this exact
    // override until an AGP upgrade bundles the same or newer R8, then remove it rather than
    // maintaining two version sources.
    buildscript {
        repositories {
            mavenCentral()
            maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UA Cast Player"
include(":app")
include(":baselineprofile")
