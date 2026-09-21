package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    private fun release(
        tag: String = "v1.2.0",
        htmlUrl: String = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0",
        body: String = "Fixed the thing",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = """
        {
          "tag_name": "$tag",
          "html_url": "$htmlUrl",
          "body": "$body",
          "draft": $draft,
          "prerelease": $prerelease
        }
    """.trimIndent()

    @Test
    fun readsTagUrlAndNotes() {
        val parsed = GitHubReleaseParser.parse(release())

        assertNotNull(parsed)
        assertEquals("v1.2.0", parsed!!.tagName)
        assertEquals("https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0", parsed.releaseUrl)
        assertEquals(AppVersion.parse("1.2.0"), parsed.version)
    }

    /** `/releases/latest` already filters these out, but this parser is the last thing between a
     * half-written draft and every user's home screen, and the check is one line. */
    @Test
    fun draftsAndPreReleasesAreIgnored() {
        assertNull(GitHubReleaseParser.parse(release(draft = true)))
        assertNull(GitHubReleaseParser.parse(release(prerelease = true)))
    }

    @Test
    fun aTagThatIsNotAVersionIsIgnoredRatherThanGuessedAt() {
        assertNull(GitHubReleaseParser.parse(release(tag = "nightly")))
        assertNull(GitHubReleaseParser.parse(release(tag = "")))
    }

    @Test
    fun missingFieldsYieldNullInsteadOfAnEmptyRelease() {
        assertNull(GitHubReleaseParser.parse("""{"html_url": "https://example.com"}"""))
        assertNull(GitHubReleaseParser.parse("""{"tag_name": "v1.0.0"}"""))
        assertNull(GitHubReleaseParser.parse("{}"))
    }

    /** An update check runs on the launch path. Whatever a proxy, a captive portal or a rate-limit
     * page returns, it must not throw. */
    @Test
    fun malformedResponsesDoNotThrow() {
        assertNull(GitHubReleaseParser.parse(""))
        assertNull(GitHubReleaseParser.parse("not json at all"))
        assertNull(GitHubReleaseParser.parse("<html><body>429</body></html>"))
        assertNull(GitHubReleaseParser.parse("""{"tag_name": """))
        assertNull(GitHubReleaseParser.parse("[]"))
    }

    private fun releaseWithAssets(assets: String) = """
        {
          "tag_name": "v1.2.0",
          "html_url": "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0",
          "draft": false,
          "prerelease": false,
          "assets": [$assets]
        }
    """.trimIndent()

    private val apkAsset = """
        {
          "name": "uacast-1.2.0.apk",
          "state": "uploaded",
          "size": 41234567,
          "content_type": "application/vnd.android.package-archive",
          "browser_download_url": "https://github.com/Dovbnyak28/UA-Cast/releases/download/v1.2.0/uacast-1.2.0.apk",
          "digest": "sha256:${"b".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun readsTheApkAttachedToARelease() {
        val apk = GitHubReleaseParser.parse(releaseWithAssets(apkAsset))?.apk

        assertNotNull(apk)
        assertEquals(
            "https://github.com/Dovbnyak28/UA-Cast/releases/download/v1.2.0/uacast-1.2.0.apk",
            apk!!.downloadUrl,
        )
        assertEquals(41_234_567L, apk.sizeBytes)
        assertEquals("b".repeat(64), apk.sha256)
    }

    /**
     * The half that has to keep working: an announcement is worth more than an install button. A
     * release with no APK, or one this build will not choose between, still has to reach the user
     * as "1.2.0 exists" so they can open the page - which is exactly what the flow did before it
     * could download anything.
     */
    @Test
    fun aReleaseWithNoUsableApkIsStillARelease() {
        val noAssets = GitHubReleaseParser.parse(release())
        assertNotNull(noAssets)
        assertNull(noAssets!!.apk)

        val emptyAssets = GitHubReleaseParser.parse(releaseWithAssets(""))
        assertNotNull(emptyAssets)
        assertNull(emptyAssets!!.apk)

        val notAnApk = GitHubReleaseParser.parse(
            releaseWithAssets("""{"name":"notes.txt","state":"uploaded","size":12,"browser_download_url":"u"}"""),
        )
        assertNotNull(notAnApk)
        assertNull(notAnApk!!.apk)
    }

    /** `digest` is nullable in the API and absent on anything published before GitHub recorded it -
     * an ordinary state, not a broken response. */
    @Test
    fun anAssetWithNoDigestStillYieldsAnApk() {
        val apk = GitHubReleaseParser.parse(
            releaseWithAssets(
                """{
                    "name":"a.apk","state":"uploaded","size":9,
                    "browser_download_url":"https://github.com/Dovbnyak28/UA-Cast/releases/download/v1.2.0/a.apk",
                    "digest":null
                }""".trimIndent(),
            ),
        )?.apk

        assertNotNull(apk)
        assertNull(apk!!.sha256)
    }

    /** One malformed attachment must not cost the release notice. */
    @Test
    fun anUnreadableAssetIsDroppedRatherThanFailingTheRelease() {
        val parsed = GitHubReleaseParser.parse(releaseWithAssets("""{"size":1}, "not an object", $apkAsset"""))

        assertNotNull(parsed)
        assertEquals("b".repeat(64), parsed!!.apk?.sha256)
    }

    @Test
    fun aReleasePageOutsideThisRepositoryIsIgnored() {
        assertNull(
            GitHubReleaseParser.parse(
                release(htmlUrl = "https://github.com/attacker/UA-Cast/releases/tag/v1.2.0"),
            ),
        )
        assertNull(
            GitHubReleaseParser.parse(
                release(htmlUrl = "http://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0"),
            ),
        )
    }

    @Test
    fun anAssetOutsideThisRepositoryIsNotDownloadable() {
        val externalAsset = apkAsset.replace(
            "https://github.com/Dovbnyak28/UA-Cast/releases/download/",
            "https://github.com/attacker/other/releases/download/",
        )

        val parsed = GitHubReleaseParser.parse(releaseWithAssets(externalAsset))

        assertNotNull(parsed)
        assertNull(parsed!!.apk)
    }

    @Test
    fun githubUrlsWithUserInfoOrTraversalAreRejected() {
        assertNull(
            GitHubReleaseParser.parse(
                release(htmlUrl = "https://user:password@github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0"),
            ),
        )
        assertNull(
            GitHubReleaseParser.parse(
                release(htmlUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/../attacker"),
            ),
        )
    }

    /**
     * The real thing: the exact document `api.github.com` returned for this app's own repository,
     * saved verbatim (less the `author` record, which this parser never looks at).
     *
     * Every other test here is written against JSON this file also wrote, which cannot catch the one
     * failure that matters most - the server sending a shape nobody anticipated. GitHub's release
     * object carries nineteen top-level fields; the hand-written fixtures above carry five.
     *
     * It also pins the state the repository is genuinely in, which is not a corner case: one
     * published release, **zero assets**. That is why the update banner on a real device offers to
     * open the release page rather than to install anything - see `UpdateBanner`. Attach the APKs to
     * a release and `apk` here stops being null; until then this documents, in the app's own tests,
     * exactly why the install path never engages.
     */
    @Test
    fun theResponseThisRepositoryActuallyReturnsIsParsedAndCarriesNoApk() {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(REAL_RESPONSE)) {
            "missing test resource $REAL_RESPONSE"
        }.use { it.readBytes().decodeToString() }

        val parsed = GitHubReleaseParser.parse(json, supportedAbis = listOf("arm64-v8a"))

        assertNotNull("the live response did not parse at all", parsed)
        assertEquals("v0.9.1", parsed!!.tagName)
        assertEquals("https://github.com/Dovbnyak28/UA-Cast/releases/tag/v0.9.1", parsed.releaseUrl)
        assertNull("this release has no assets, so there is nothing to install", parsed.apk)
    }

    /**
     * The same repository once the APKs were attached, saved verbatim the moment it went live.
     *
     * Together with the case above this is the whole of what the update flow decides, taken from
     * the server rather than from anyone's idea of what the server sends: with no assets there is
     * nothing to install and the banner can only offer the release page; with them, the universal
     * APK is chosen over three per-ABI builds and carries a digest to verify against.
     *
     * The digest arrives as `sha256:<hex>` - GitHub's own prefixed form, which is not what
     * [com.uacastplayer.data.update.UpdateDownloader] compares against and is exactly the kind of
     * detail a hand-written fixture gets wrong by writing bare hex.
     */
    @Test
    fun theResponseWithApksAttachedPicksTheUniversalBuild() {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(REAL_RESPONSE_WITH_APKS)) {
            "missing test resource $REAL_RESPONSE_WITH_APKS"
        }.use { it.readBytes().decodeToString() }

        val parsed = GitHubReleaseParser.parse(json, supportedAbis = listOf("arm64-v8a"))

        assertNotNull("the live response did not parse", parsed)
        assertEquals("v0.9.1", parsed!!.tagName)
        val apk = checkNotNull(parsed.apk) { "no APK was picked from four attached assets" }
        assertTrue(
            "the universal APK must win over the per-ABI builds, got ${apk.downloadUrl}",
            apk.downloadUrl.endsWith("app-universal-release.apk"),
        )
        assertEquals(UNIVERSAL_BYTES, apk.sizeBytes)
        assertEquals("the sha256: prefix was not stripped", UNIVERSAL_SHA256, apk.sha256)
    }

    private companion object {
        const val REAL_RESPONSE = "github-latest-release-no-assets.json"
        const val REAL_RESPONSE_WITH_APKS = "github-latest-release-with-apks.json"
        const val UNIVERSAL_BYTES = 23_773_806L
        const val UNIVERSAL_SHA256 = "b66cb0f9c7de0e7468d86a57c0c9fca20134d72c1271cff93ba24d6fbd3e70c8"
    }
}
