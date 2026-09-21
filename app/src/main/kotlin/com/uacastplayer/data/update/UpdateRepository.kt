package com.uacastplayer.data.update

import android.os.Build
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.GitHubReleaseParser
import com.uacastplayer.update.ReleaseHttpStatus
import com.uacastplayer.update.ReleaseLookup
import com.uacastplayer.update.ReleaseSource
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
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
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) : ReleaseSource {
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 10, readTimeoutSeconds = 15)

    /**
     * Returns the newest published release, or which kind of nothing came back.
     *
     * The causes of *failure* are still not distinguished - offline, rate-limited and a 5xx all
     * produce [ReleaseLookup.Failed], because every caller does the same thing with them and a
     * taxonomy nobody branches on is not worth carrying. The cause is logged instead. "No release
     * published" is not one of them; see [ReleaseLookup.NonePublished].
     */
    override suspend fun fetchLatestRelease(): ReleaseLookup = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(releasesUrl)
                // GitHub's documented Accept header for the REST API; without it the response format is
                // whatever the current default happens to be.
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            httpClient.newCall(request).executeCancellable { response ->
                val settledByStatus = ReleaseHttpStatus.readStatus(response.code)
                if (settledByStatus != null) {
                    if (settledByStatus == ReleaseLookup.NonePublished) {
                        // Not a warning: this is what a repository with no release yet looks like,
                        // and it is the normal state of things before the first one is cut.
                        AppLog.d(TAG) { "update check: no release published (HTTP ${response.code})" }
                    } else {
                        AppLog.w(TAG) { "update check refused: HTTP ${response.code}" }
                    }
                    settledByStatus
                } else {
                    // peekBody rather than body.string(): it stops at the cap instead of trusting a
                    // Content-Length that a response is under no obligation to tell the truth about.
                    val json = response.peekBody(MAX_BODY_BYTES).string()
                    // Build.SUPPORTED_ABIS is read here, at the one place with an Android runtime
                    // under it, and handed to a rule that stays pure. It only ever decides between
                    // per-ABI APKs when no universal one was published - see ReleaseApkPolicy.
                    val release = GitHubReleaseParser.parse(json, Build.SUPPORTED_ABIS.orEmpty().toList())
                    if (release == null) {
                        // A release exists but cannot be read - a draft that slipped through, or a tag
                        // that is not a version. Not "nothing published": something is, and this build
                        // failed to understand it.
                        AppLog.w(TAG) { "update check: response was not a usable release" }
                        ReleaseLookup.Failed
                    } else {
                        ReleaseLookup.Found(release)
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            AppLog.w(TAG) { "update endpoint rejected: ${e.javaClass.simpleName}" }
            ReleaseLookup.Failed
        } catch (e: IOException) {
            // Offline, DNS failure, TLS failure, timeout - all ordinary, none worth a user-visible
            // error when the check was the app's own idea rather than the user's.
            AppLog.w(TAG) { "update check failed: ${e.javaClass.simpleName}" }
            ReleaseLookup.Failed
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Dovbnyak28/UA-Cast/releases/latest"
    }
}
