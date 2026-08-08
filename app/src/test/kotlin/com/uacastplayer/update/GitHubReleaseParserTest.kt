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

}
