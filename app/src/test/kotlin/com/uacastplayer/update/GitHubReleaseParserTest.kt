package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
          "browser_download_url": "https://github.com/x/y/releases/download/v1.2.0/uacast-1.2.0.apk",
          "digest": "sha256:${"b".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun readsTheApkAttachedToARelease() {
        val apk = GitHubReleaseParser.parse(releaseWithAssets(apkAsset))?.apk

        assertNotNull(apk)
        assertEquals("https://github.com/x/y/releases/download/v1.2.0/uacast-1.2.0.apk", apk!!.downloadUrl)
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
                """{"name":"a.apk","state":"uploaded","size":9,"browser_download_url":"u","digest":null}""",
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
}
