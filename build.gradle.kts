plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Packaging needs llvm-strip too, even when native code was built in the separate decoder job.
// Use that job's locked toolchain instead of AGP's unrelated default NDK revision.
val decoderSources = groovy.json.JsonSlurper().parse(file("ffmpegdecoder/native-sources.json")) as Map<*, *>
val decoderNdkVersion = (decoderSources["toolchain"] as Map<*, *>)["ndk"] as String
subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            ndkVersion = decoderNdkVersion
        }
    }
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            ndkVersion = decoderNdkVersion
        }
    }
}
