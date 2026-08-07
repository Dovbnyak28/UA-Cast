package com.uacastplayer.data.update

import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.GitHubReleaseParser
import com.uacastplayer.update.ReleaseSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val TAG = "UpdateRepository"

/** How much of a response is worth reading before concluding this is not a release document.
 * Release notes are prose; a megabyte of it would already be unusual, and the cap means a hostile
 * or misconfigured endpoint cannot make the app read forever on the launch path. */
private const val MAX_BODY_BYTES = 1L * 1024 * 1024

/**
 * Asks GitHub what the newest published release is.
 *
 * Read-only, unauthenticated, one request. GitHub's unauthenticated limit is 60 requests per hour
 * per IP, which a check that runs at most weekly per device cannot approach - and no token is
 * embedded, because a token shipped inside an APK is a published token.
 */
class UpdateRepository(
    private val releasesUrl: String = LATEST_RELEASE_URL,
) : ReleaseSource {
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)

    /**
     * Returns the newest published release, or null if that could not be established for any
     * reason - offline, rate-limited, a 5xx, a draft, a tag that is not a version number.
     *
     * One null for every failure is deliberate. Every caller does the same thing with it (says
     * nothing, or reports "could not check"), so distinguishing the causes here would only produce
     * a taxonomy nobody branches on. The cause is logged instead.
     */
    override suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(releasesUrl)
            // GitHub's documented Accept header for the REST API; without it the response format is
            // whatever the current default happens to be.
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG) { "update check refused: HTTP ${response.code}" }
                    return@withContext null
                }
                // peekBody rather than body.string(): it stops at the cap instead of trusting a
                // Content-Length that a response is under no obligation to tell the truth about.
                val json = response.peekBody(MAX_BODY_BYTES).string()
                GitHubReleaseParser.parse(json).also {
                    if (it == null) AppLog.w(TAG) { "update check: response was not a usable release" }
                }
            }
        } catch (e: IOException) {
            // Offline, DNS failure, TLS failure, timeout - all ordinary, none worth a user-visible
            // error when the check was the app's own idea rather than the user's.
            AppLog.w(TAG) { "update check failed: ${e.javaClass.simpleName}" }
            null
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Dovbnyak28/UA-Cast/releases/latest"
    }
}
