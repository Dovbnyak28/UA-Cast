package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which attached file a release is willing to install, and which published hash it will trust.
 *
 * Both halves exist to keep a bad answer from reaching [com.uacastplayer.data.update.ApkInstaller],
 * where every wrong answer fails the same unreadable way: after the download, in a system dialog,
 * with a message the user can do nothing with.
 *
 * A release of this app carries **four** APKs - three per-ABI and one universal - so "which one" is
 * a question that has to be answered rather than dodged. It is answered by rule where a rule exists
 * (universal always installs; failing that, an architecture this device runs) and refused where one
 * does not. Accepting a digest that only looks like one would be worse than having none, because
 * the check would then pass having verified nothing.
 */
class ReleaseApkPolicyTest {

    private fun asset(
        name: String = "uacast-0.9.1.apk",
        state: String = "uploaded",
        url: String = "https://example.test/$name",
        size: Long = 40_000_000L,
        digest: String? = null,
    ) = ReleaseAsset(name, state, url, size, digest)

    @Test
    fun `the one uploaded apk is the one that is picked`() {
        val picked = ReleaseApkPolicy.pick(
            listOf(asset(name = "mapping.txt"), asset(), asset(name = "checksums.txt")),
        )

        assertEquals("https://example.test/uacast-0.9.1.apk", picked?.downloadUrl)
        assertEquals(40_000_000L, picked?.sizeBytes)
    }

    /**
     * What a release of this app actually looks like: four APKs, three per-ABI and one universal
     * (`./gradlew :app:assembleRelease`, see the `splits` block).
     *
     * Universal wins even though it is twice the download, and the reason is this project's own
     * versionCode scheme rather than taste: each per-ABI APK gets `base × 10 + an ABI digit` and
     * universal deliberately takes the highest, so a device already on universal cannot install a
     * per-ABI APK of the same release at all - Android refuses it as a downgrade, after the
     * download, in a system dialog.
     */
    @Test
    fun `a full release picks the universal apk whatever this device runs`() {
        val release = listOf(
            asset(name = "app-armeabi-v7a-release.apk"),
            asset(name = "app-arm64-v8a-release.apk"),
            asset(name = "app-x86_64-release.apk"),
            asset(name = "app-universal-release.apk"),
        )

        val picked = ReleaseApkPolicy.pick(release, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertEquals("https://example.test/app-universal-release.apk", picked?.downloadUrl)
    }

    /** Only when no universal APK was published does the architecture decide, and it takes
     * `Build.SUPPORTED_ABIS` in its own order - most preferred first. */
    @Test
    fun `without a universal apk this device's own architecture decides`() {
        val release = listOf(
            asset(name = "app-armeabi-v7a-release.apk"),
            asset(name = "app-arm64-v8a-release.apk"),
            asset(name = "app-x86_64-release.apk"),
        )

        val picked = ReleaseApkPolicy.pick(release, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertEquals("https://example.test/app-arm64-v8a-release.apk", picked?.downloadUrl)
    }

    /** A 64-bit phone lists the 32-bit ABI too, second. With no arm64 build published it should take
     * the one it can actually run rather than refusing. */
    @Test
    fun `a later supported architecture is taken when the preferred one is absent`() {
        val release = listOf(asset(name = "app-armeabi-v7a-release.apk"), asset(name = "app-x86_64-release.apk"))

        val picked = ReleaseApkPolicy.pick(release, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertEquals("https://example.test/app-armeabi-v7a-release.apk", picked?.downloadUrl)
    }

    /**
     * Still refused rather than guessed: several APKs, none universal, none this device can run.
     * That is a release built for architectures this phone is not - or a debug build published
     * beside a release one, and an APK signed with the debug key cannot install over a
     * release-signed app under any circumstances.
     */
    @Test
    fun `apks this device cannot run are refused rather than guessed between`() {
        val release = listOf(asset(name = "app-x86_64-release.apk"), asset(name = "app-debug.apk"))

        assertNull(ReleaseApkPolicy.pick(release, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")))
    }

    /** One APK is still one APK - the ordinary hand-published release, with no ABI question to
     * answer. */
    @Test
    fun `a single apk is taken without consulting the architecture at all`() {
        val picked = ReleaseApkPolicy.pick(listOf(asset(name = "app-x86_64-release.apk")), supportedAbis = emptyList())

        assertEquals("https://example.test/app-x86_64-release.apk", picked?.downloadUrl)
    }

    /**
     * `open` is GitHub's word for an upload that never finished. Downloading one gives a truncated
     * APK, and the resulting install error says nothing about why.
     */
    @Test
    fun `an upload that never finished is not an apk to install`() {
        assertNull(ReleaseApkPolicy.pick(listOf(asset(state = "open"))))
    }

    /** A half-open release with one finished APK beside one still uploading is not ambiguous - only
     * one of them can be installed, so the rule above must not fire on it. */
    @Test
    fun `a finished apk still wins beside one that is still uploading`() {
        val picked = ReleaseApkPolicy.pick(
            listOf(asset(name = "old.apk", state = "open"), asset(name = "new.apk")),
        )

        assertEquals("https://example.test/new.apk", picked?.downloadUrl)
    }

    @Test
    fun `a release with nothing attached simply has no apk`() {
        assertNull(ReleaseApkPolicy.pick(emptyList()))
    }

    @Test
    fun `an empty file and a blank url are both refused`() {
        assertNull(ReleaseApkPolicy.pick(listOf(asset(size = 0L))))
        assertNull(ReleaseApkPolicy.pick(listOf(asset(url = "  "))))
    }

    @Test
    fun `the extension is matched whatever case it was uploaded in`() {
        assertEquals(
            "https://example.test/UACast.APK",
            ReleaseApkPolicy.pick(listOf(asset(name = "UACast.APK")))?.downloadUrl,
        )
    }

    private val hex = "a".repeat(64)

    @Test
    fun `a published sha256 comes through as plain lowercase hex`() {
        assertEquals(hex, ReleaseApkPolicy.pick(listOf(asset(digest = "sha256:$hex")))?.sha256)
    }

    /** Absent is an ordinary state: GitHub only began recording digests later, so assets published
     * before that have none. It must read as "no hash", never as a hash that fails to match. */
    @Test
    fun `no published digest is null rather than a value that cannot match`() {
        assertNull(ReleaseApkPolicy.pick(listOf(asset(digest = null)))?.sha256)
        assertNull(ReleaseDigest.parse(null))
        assertNull(ReleaseDigest.parse(""))
    }

    /**
     * Everything that is shaped like a digest and is not one. Each of these would otherwise be
     * carried into the download as a hash to check against, and a hash that can never match turns
     * every update into a failed one.
     */
    @Test
    fun `anything that is not exactly a sha256 is no digest at all`() {
        assertNull("another algorithm", ReleaseDigest.parse("sha1:${"a".repeat(40)}"))
        assertNull("no prefix", ReleaseDigest.parse(hex))
        assertNull("too short", ReleaseDigest.parse("sha256:${"a".repeat(63)}"))
        assertNull("too long", ReleaseDigest.parse("sha256:${"a".repeat(65)}"))
        assertNull("not hex", ReleaseDigest.parse("sha256:${"g".repeat(64)}"))
        assertNull("uppercase", ReleaseDigest.parse("sha256:${"A".repeat(64)}"))
    }

    /** Surrounding whitespace is a transport artefact, not a different digest. */
    @Test
    fun `a digest is read through the whitespace around it`() {
        assertEquals(hex, ReleaseDigest.parse("  sha256:$hex\n"))
    }
}
