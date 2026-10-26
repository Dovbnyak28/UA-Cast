import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.nio.file.Files

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

@org.gradle.work.DisableCachingByDefault(because = "Validation has no output and must fail closed on missing native files")
abstract class VerifySoftwareDecoderNative : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeDirectory: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: ConfigurableFileCollection
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:Input abstract val media3Version: Property<String>

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65_536)
            while (true) {
                val size = stream.read(buffer)
                if (size == -1) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    @TaskAction fun verify() {
        val repository = repositoryDirectory.get().asFile
        val directory = nativeDirectory.get().asFile
        val manifestFile = directory.resolve("manifest.json")
        check(manifestFile.isFile) { "Software native manifest missing. Run scripts/prepare-software-decoder.py first." }
        val parser = JsonSlurper()
        val manifest = parser.parse(manifestFile) as? Map<*, *> ?: error("Invalid software native manifest")
        val lock = parser.parse(repository.resolve("ffmpegdecoder/native-sources.json")) as Map<*, *>
        check(lock["media3"] == media3Version.get()) { "Refresh/test the software wrapper before changing Media3." }
        check(manifest["schemaVersion"] == 1 && manifest["toolchain"] == lock["toolchain"]) { "Native schema/toolchain mismatch" }
        val sourceHashes = nativeSources.files.associate { file ->
            file.relativeTo(repository).invariantSeparatorsPath to hash(file)
        }
        check(manifest["inputs"] == sourceHashes) { "Software native outputs are stale; rerun the verified bootstrap." }
        val expectedArchives = listOf("ffmpeg", "libyuv").associateWith { (lock[it] as Map<*, *>)["sha256"] }
        check(manifest["archives"] == expectedArchives) { "Software native archive provenance mismatch" }
        val abis = mapOf("armeabi-v7a" to (1 to 40), "arm64-v8a" to (2 to 183), "x86_64" to (2 to 62))
        val libraries = listOf("libavcodec.so", "libavutil.so", "libswresample.so", "libswscale.so", "libffmpegJNI.so")
        val expected = abis.keys.flatMap { abi -> libraries.map { "$abi/$it" } }.toSet()
        val hashes = manifest["libraries"] as? Map<*, *> ?: error("Native library hashes missing")
        check(hashes.keys == expected) { "All five software libraries must be present for each of the three ABIs." }
        val present = directory.walkTopDown().filter { it.isFile && it.extension == "so" }
            .map { it.relativeTo(directory).invariantSeparatorsPath }.toSet()
        check(present == expected) { "Unexpected/missing native library; refusing hardware-only or GPL-wrapper packaging." }
        for (relative in expected) {
            val file = directory.resolve(relative)
            check(file.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath()) &&
                !Files.isSymbolicLink(file.toPath()) && hash(file) == hashes[relative]) { "Invalid native file: $relative" }
            val header = ByteArray(20)
            file.inputStream().use { check(it.read(header) == header.size) { "Truncated ELF: $relative" } }
            val (elfClass, machine) = abis.getValue(relative.substringBefore('/'))
            check(header.take(4) == listOf(127.toByte(), 69.toByte(), 76.toByte(), 70.toByte()) &&
                header[4].toInt() == elfClass && header[5].toInt() == 1 &&
                (header[18].toInt() and 255) + ((header[19].toInt() and 255) shl 8) == machine) { "Wrong native ABI: $relative" }
        }
    }
}

val verifySoftwareNative = tasks.register<VerifySoftwareDecoderNative>("verifySoftwareDecoderNative") {
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    nativeDirectory.set(layout.buildDirectory.dir("native-libraries"))
    media3Version.set(libs.versions.media3)
    nativeSources.from(rootProject.files(
        "ffmpegdecoder/native-sources.json", "ffmpegdecoder/src/main/jni/CMakeLists.txt",
        "ffmpegdecoder/src/main/jni/ffmpeg_jni.cc", "scripts/build-ffmpeg-android.sh",
        "scripts/patches/ffmpeg-6.1.6-windows-response.patch", "scripts/prepare-software-decoder.py",
    ))
}
tasks.configureEach {
    if (name.matches(Regex("merge.*(?:NativeLibs|JniLibFolders)|bundle.*Aar"))) dependsOn(verifySoftwareNative)
}

// Match the app's Kotlin model, including the lint compiler's metadata interpretation.
kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").jniLibs.srcDir("build/native-libraries")
    // A library has no application target SDK; its standalone instrumentation APK does.
    testOptions { targetSdk = 36 }
}

dependencies {
    api(libs.media3.exoplayer)
    implementation("androidx.annotation:annotation-jvm:1.10.0")
    constraints {
        implementation("androidx.annotation:annotation-experimental:1.4.1")
        implementation("androidx.annotation:annotation:1.8.0")
        implementation("androidx.exifinterface:exifinterface:1.4.2")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
