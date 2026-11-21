import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Supplied by the root Kotlin Android plugin's KGP classpath, using the same pinned version.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.detekt)
}

// Policies/codecs have no Android, UI, networking or app dependency on their compile classpath.
// Compile with the same JDK as CI, emitting the app's Java 17 bytecode target.
kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

dependencies {
    testImplementation(libs.junit)
}
