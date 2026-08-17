package com.uacastplayer.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [GitHubReleaseParser] against the `org.json` that actually runs on a phone.
 *
 * `GitHubReleaseParserTest` beside this one runs on the reference `org.json` from Maven
 * (`testImplementation(libs.org.json)`), and the two disagree about an explicit JSON null:
 * measured on `{"a":null}`, the reference answers `""` and Android's answers `"null"`.
 *
 * That matters more here than anywhere else in this app, because this document is not a local file
 * a user might hand-edit - it is GitHub's own API response, fetched on every update check, and
 * `"digest": null` is what GitHub sends for every asset uploaded before it began recording one.
 */
@RunWith(RobolectricTestRunner::class)
class GitHubReleaseParserOnAndroidJsonTest {

    /** If this ever reads `""`, the runner has stopped using Android's `org.json` and the rest of
     * this class has stopped testing anything. */
    @Test
    fun `the runner really is on android's org-json`() {
        assertEquals("null", JSONObject("""{"a":null}""").optString("a"))
    }

    private fun release(assets: String = "[]") = """
        {
          "tag_name": "v1.2.0",
          "html_url": "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.2.0",
          "draft": false,
          "prerelease": false,
          "assets": $assets
        }
    """.trimIndent()

    /**
     * The case GitHub actually produces. The outcome was already right - `"null"` is not a
     * `sha256:` digest any more than `""` is - but it was right by accident, and the comment on
     * that line asserted the reference implementation's behaviour as fact.
     */
    @Test
    fun `an asset with an explicitly null digest has no digest`() {
        val parsed = GitHubReleaseParser.parse(
            release(
                """
                [{
                  "name": "app-universal-release.apk",
                  "state": "uploaded",
                  "browser_download_url": "https://example.test/app.apk",
                  "size": 40000000,
                  "digest": null
                }]
                """.trimIndent(),
            ),
        )

        assertNull("a null digest arrived as text", parsed?.apk?.sha256)
        assertEquals(40_000_000L, parsed?.apk?.sizeBytes)
    }

    /** A digest that is present still reaches the downloader, so the guard cannot be "always null". */
    @Test
    fun `a real digest still arrives`() {
        val hex = "b66cb0f9c7de0e7468d86a57c0c9fca20134d72c1271cff93ba24d6fbd3e70c8"
        val parsed = GitHubReleaseParser.parse(
            release(
                """
                [{
                  "name": "app-universal-release.apk",
                  "state": "uploaded",
                  "browser_download_url": "https://example.test/app.apk",
                  "size": 40000000,
                  "digest": "sha256:$hex"
                }]
                """.trimIndent(),
            ),
        )

        assertEquals(hex, parsed?.apk?.sha256)
    }

    /**
     * The one that would have been offered to a user: a null `html_url` became the four-character
     * string `"null"`, which the update banner hands to a browser as the release page.
     */
    @Test
    fun `a release with no page is not announced at all`() {
        val parsed = GitHubReleaseParser.parse(
            """{"tag_name":"v1.2.0","html_url":null,"draft":false,"prerelease":false,"assets":[]}""",
        )

        assertNull("a release was announced with \"null\" as its page", parsed)
    }

    @Test
    fun `a release with no tag is not announced at all`() {
        val parsed = GitHubReleaseParser.parse(
            """{"tag_name":null,"html_url":"https://example.test/r","draft":false,"prerelease":false,"assets":[]}""",
        )

        assertNull(parsed)
    }

    /** An asset whose own fields are null is dropped, leaving the release itself announceable -
     * the fallback this parser is built around, where the user opens the page themselves. */
    @Test
    fun `an asset with null fields is dropped without losing the release`() {
        val parsed = GitHubReleaseParser.parse(
            release("""[{"name":null,"state":null,"browser_download_url":null,"size":0,"digest":null}]"""),
        )

        assertEquals("v1.2.0", parsed?.tagName)
        assertNull("an unusable asset was offered as an APK", parsed?.apk)
    }
}
