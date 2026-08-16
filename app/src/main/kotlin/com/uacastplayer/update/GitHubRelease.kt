package com.uacastplayer.update

import org.json.JSONException
import org.json.JSONObject

/**
 * The parts of a GitHub release this app cares about: which version it is, where a human can go to
 * read about it, and the APK to install.
 *
 * The release notes are still not kept. They live on the page [releaseUrl] points at, so carrying a
 * copy through the app would be a field nothing reads.
 *
 * [apk] is null far more often than it is a fault: a release with no APK attached, one whose upload
 * never finished, or one carrying several that [ReleaseApkPolicy] cannot choose between. Every one
 * of those falls back to the same offer this flow used to make on its own - open the release page in
 * a browser and let the user see what they are installing.
 */
data class GitHubRelease(
    val version: AppVersion,
    val tagName: String,
    val releaseUrl: String,
    val apk: ReleaseApk? = null,
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
    fun parse(json: String, supportedAbis: List<String> = emptyList()): GitHubRelease? {
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
            // Mechanical: which of the attached files is the one to install is [ReleaseApkPolicy]'s
            // decision, kept out of here so it can be tested without a JSON document around it.
            apk = ReleaseApkPolicy.pick(readAssets(obj), supportedAbis),
        )
    }

    /**
     * The `assets` array, with anything unreadable dropped rather than failing the whole release.
     *
     * A release that this build cannot take an APK from is still a release worth announcing - the
     * user opens the page and downloads it themselves, which is what the flow did before there was
     * an APK field at all. Losing the version notice as well, because one attachment had a shape
     * this parser did not expect, would be trading the working half for the new one.
     */
    private fun readAssets(obj: JSONObject): List<ReleaseAsset> {
        val array = obj.optJSONArray("assets") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val asset = array.optJSONObject(index) ?: return@mapNotNull null
            val name = asset.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ReleaseAsset(
                name = name,
                state = asset.optString("state"),
                downloadUrl = url,
                sizeBytes = asset.optLong("size", 0L),
                // optString turns JSON null into "", which is not a digest either - both mean the
                // same thing here and ReleaseDigest.parse answers both with null.
                digest = asset.optString("digest").takeIf { it.isNotBlank() },
            )
        }
    }
}
