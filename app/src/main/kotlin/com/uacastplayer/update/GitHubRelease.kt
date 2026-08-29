package com.uacastplayer.update

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import java.net.URI
import java.util.Locale
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

    fun parse(json: String, supportedAbis: List<String> = emptyList()): GitHubRelease? =
        runCatchingNonFatal { JSONObject(json) }
            .getOrNull()
            ?.takeUnless { obj -> obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false) }
            ?.let { obj -> releaseFrom(obj, supportedAbis) }

    private fun releaseFrom(obj: JSONObject, supportedAbis: List<String>): GitHubRelease? {
        val tagName = obj.stringOrNull("tag_name")
        val version = tagName?.let(AppVersion::parse)
        val releaseUrl = obj.stringOrNull("html_url")?.takeIf(::isTrustedReleaseUrl)
        return if (tagName != null && version != null && releaseUrl != null) {
            GitHubRelease(
                version = version,
                tagName = tagName,
                releaseUrl = releaseUrl,
                // Mechanical: which attached file to install is [ReleaseApkPolicy]'s decision.
                apk = ReleaseApkPolicy.pick(readAssets(obj), supportedAbis),
            )
        } else {
            null
        }
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
            val name = asset.stringOrNull("name") ?: return@mapNotNull null
            val url = asset.stringOrNull("browser_download_url")
                ?.takeIf(::isTrustedDownloadUrl)
                ?: return@mapNotNull null
            ReleaseAsset(
                name = name,
                state = asset.stringOrNull("state").orEmpty(),
                downloadUrl = url,
                sizeBytes = asset.optLong("size", 0L),
                // GitHub sends `"digest": null` for every asset uploaded before it began recording
                // one - see ReleaseApk, where that absence is documented as ordinary rather than a
                // fault. This is the field in this file most likely to actually be null.
                digest = asset.stringOrNull("digest"),
            )
        }
    }

    /**
     * A field's value, or null when it is absent, explicitly `null`, or blank.
     *
     * `optString(name).takeIf { it.isNotBlank() }` was doing this, and it does not, on the platform
     * this runs on. The two `org.json` implementations disagree, measured directly on `{"a":null}`:
     * the reference one - which `testImplementation(libs.org.json)` puts under the unit tests -
     * answers `""`, while Android's answers `"null"`, four characters, because `optString` goes
     * through `JSON.toString(opt(name))` and `JSONObject.NULL.toString()` is `"null"`. So a blank
     * check was being handed a non-blank string for a field that was explicitly null, and only on a
     * device.
     *
     * The outcomes here happened to survive it - a digest of `"null"` is refused by
     * [ReleaseDigest.parse] just as a missing one is, and an asset named `"null"` does not end in
     * `.apk` - but `html_url` would have become the literal string `"null"` and been offered to the
     * user as the release page. The comment that used to sit on the digest line asserted the
     * reference behaviour as fact, which is the part worth removing: the next reader would have
     * believed it.
     *
     * `isNull` is the one accessor both implementations agree on, and it is true for an absent name
     * as well as an explicit null. See `BackupCodec.stringOrNull`, which is the same guard for the
     * same reason.
     */
    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }

    /**
     * The release document is network input, even though it came from the expected API endpoint.
     * Keep a compromised/misconfigured response from turning the update banner into a link to a
     * phishing page. The repository path is part of the trust boundary: a link to another
     * github.com project is still not this app's release page.
     */
    private fun isTrustedReleaseUrl(value: String): Boolean =
        isTrustedGithubUrl(value, pathPrefix = "$REPOSITORY_PATH/releases/")

    /**
     * Only GitHub's release-asset path is accepted. OkHttp may follow GitHub's normal HTTPS
     * redirect to its asset CDN after this check; an arbitrary HTTP/external URL never reaches it.
     */
    private fun isTrustedDownloadUrl(value: String): Boolean =
        isTrustedGithubUrl(value, pathPrefix = "$REPOSITORY_PATH/releases/download/")

    private fun isTrustedGithubUrl(value: String, pathPrefix: String): Boolean = runCatchingNonFatal {
        val uri = URI(value)
        val path = uri.path ?: return@runCatchingNonFatal false
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(GITHUB_HOST, ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.normalize().path == path &&
            path.lowercase(Locale.ROOT).startsWith(pathPrefix)
    }.getOrDefault(false)

    private const val GITHUB_HOST = "github.com"
    private const val REPOSITORY_PATH = "/dovbnyak28/ua-cast"
}
