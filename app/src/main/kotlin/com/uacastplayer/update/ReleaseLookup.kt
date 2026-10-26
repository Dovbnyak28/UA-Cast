package com.uacastplayer.update

/**
 * What asking GitHub for the newest release produced.
 *
 * The three cases exist because two of them used to be one. Every failure was a single `null`, on
 * the reasoning that every caller reacts to them identically - which was true of *failures*, and
 * hid the fact that "this repository has published no release at all" is not one. That state
 * answers the user's question perfectly well: nothing newer exists, so the installed build is the
 * newest there is. Reported as a failure instead, it told every user to try again later about a
 * condition retrying cannot change, and it is the state every install is in until the first release
 * is cut - the exact window in which the update check is least able to explain itself.
 *
 * `docs/RELEASING.md` already described the behaviour this type makes possible ("until the release
 * is published, every installed copy is correctly told it is up to date"); the code did the
 * opposite.
 */
sealed interface ReleaseLookup {

    /** A published release was found and read. It may or may not be newer than what is installed. */
    data class Found(val release: GitHubRelease) : ReleaseLookup

    /**
     * The endpoint answered, and the answer was that there is no published release.
     *
     * GitHub returns 404 both for this and for a repository that does not exist - renamed, deleted,
     * or made private again - and one unauthenticated request cannot tell those apart. Both are
     * treated as "nothing newer", knowingly: a second request to `/repos/{owner}/{repo}` to
     * separate them would double the cost of every check on the launch path to change a message the
     * user can do nothing about either way, while the case it would distinguish is one no user can
     * act on and the case it would break is one every user hits before the first release.
     */
    data object NonePublished : ReleaseLookup

    /** Offline, rate-limited, a 5xx, or a response that is not a release this app can read. */
    data object Failed : ReleaseLookup
}

/**
 * What an HTTP status from `/releases/latest` settles on its own.
 *
 * Separated from the request so the mapping can be tested without a server - there is no
 * MockWebServer in this project (see `CastRoutingIntegrationTest`), and this is the whole of the
 * decision worth testing.
 */
object ReleaseHttpStatus {

    private const val FIRST_SUCCESS = 200
    private const val LAST_SUCCESS = 299
    private const val NOT_FOUND = 404

    /**
     * The lookup this [code] determines by itself, or null when it determines nothing and the body
     * has to be read - which is every success, since a 200 can still carry a draft, a pre-release
     * or a tag that is not a version number.
     */
    fun readStatus(code: Int): ReleaseLookup? = when {
        code in FIRST_SUCCESS..LAST_SUCCESS -> null
        code == NOT_FOUND -> ReleaseLookup.NonePublished
        else -> ReleaseLookup.Failed
    }
}
