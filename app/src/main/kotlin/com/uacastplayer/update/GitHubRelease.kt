package com.uacastplayer.update

import org.json.JSONException
import org.json.JSONObject

/**
 * The parts of a GitHub release this app cares about: which version it is, and where a human can
 * go to read about it and download it.
 *
 * Neither a download URL nor the release notes are kept. The flow deliberately ends at the release
 * page in a browser rather than fetching an APK itself - installing one needs
 * `REQUEST_INSTALL_PACKAGES`, and letting the user see what they are installing is worth two extra
 * taps for an app distributed by sideload. The notes live on that page, so carrying a copy of them
 * through the app would be a field nothing reads.
 */
data class GitHubRelease(
    val version: AppVersion,
    val tagName: String,
    val releaseUrl: String,
)

/**
 * Reads the response of `GET /repos/{owner}/{repo}/releases/latest`.
 *
 * `latest` already excludes drafts and pre-releases, but both are re-checked here: the endpoint is
 * the only thing standing between a half-finished draft and every user's home screen, and a second
 * check costs one line. Anything unparseable returns null rather than throwing - a malformed
 * release must leave the app silent, not crash it on launch.
 */
object GitHubReleaseParser {

    // One guard per field that can be missing or wrong, and the JSONException is the "this is not
    // JSON" signal itself rather than an error with anything more to say - a captive portal's login
    // page reaching here is ordinary, not exceptional.
    @Suppress("ReturnCount", "SwallowedException")
    fun parse(json: String): GitHubRelease? {
        val obj = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }

        if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) return null

        val tagName = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val version = AppVersion.parse(tagName) ?: return null
        val releaseUrl = obj.optString("html_url").takeIf { it.isNotBlank() } ?: return null

        return GitHubRelease(
            version = version,
            tagName = tagName,
            releaseUrl = releaseUrl,
        )
    }
}
