package com.uacastplayer.update

/**
 * One file attached to a release, as GitHub reports it - the raw shape, before any judgement about
 * whether it is the APK this app should install.
 *
 * @param state GitHub's own upload state. `uploaded` is a finished file; `open` is one whose upload
 *   never completed, and downloading that gives a truncated APK that fails to install with an error
 *   the user cannot act on.
 * @param digest the published SHA-256, in GitHub's `sha256:<hex>` form. Nullable in the API and
 *   genuinely absent on assets uploaded before GitHub began recording it, so its absence is an
 *   ordinary state rather than a fault.
 */
data class ReleaseAsset(
    val name: String,
    val state: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val digest: String?,
)

/**
 * The APK this app may fetch for a release, once one has been picked and its digest understood.
 *
 * @param sha256 lowercase hex, or null when the release published none. See
 *   [com.uacastplayer.data.update.UpdateDownloader] for what null means at download time - the
 *   short version is that TLS to GitHub already covers the transfer, and the signature check is the
 *   boundary that actually decides whether anything gets installed.
 */
data class ReleaseApk(
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

/**
 * Which attached file, if any, is the APK for this release.
 *
 * **Ambiguity is answered by refusing, not by guessing.** A release carrying more than one APK is
 * either per-ABI splits or a debug build published beside the release one, and both make the choice
 * consequential: an APK signed with the debug key cannot install over a release-signed app at all
 * (Android refuses a signature change outright), and the wrong ABI installs and then fails to run.
 * Neither failure is one the user can read anything useful out of. Refusing leaves the release page
 * as the offer, where a human can see what the files are - two extra taps against an install that
 * cannot work.
 *
 * That is a rule about *this* project's releases, which publish one universal APK. Choosing between
 * splits would mean matching `Build.SUPPORTED_ABIS` against filenames, which is a different feature
 * with its own failure modes, and is deliberately not smuggled in here.
 */
object ReleaseApkPolicy {

    private const val APK_SUFFIX = ".apk"
    private const val STATE_UPLOADED = "uploaded"

    fun pick(assets: List<ReleaseAsset>): ReleaseApk? {
        val candidates = assets.filter { asset ->
            asset.name.endsWith(APK_SUFFIX, ignoreCase = true) &&
                asset.state == STATE_UPLOADED &&
                asset.sizeBytes > 0 &&
                asset.downloadUrl.isNotBlank()
        }
        val only = candidates.singleOrNull() ?: return null
        return ReleaseApk(
            downloadUrl = only.downloadUrl,
            sizeBytes = only.sizeBytes,
            sha256 = ReleaseDigest.parse(only.digest),
        )
    }
}

/**
 * Reads GitHub's `sha256:<hex>` asset digest.
 *
 * Anything that is not exactly that becomes null - a different algorithm, a truncated hex string,
 * uppercase, or the field being absent. Null and "wrong" are the same answer on purpose: both mean
 * there is no usable published hash, and the alternative - accepting a value that only looks like
 * one - would let a check pass that verified nothing. A caller that has a hash must match it; a
 * caller that has none must say so rather than pretend.
 */
object ReleaseDigest {

    private const val PREFIX = "sha256:"
    private const val HEX_LENGTH = 64

    // Lowercase only, not lowercased: the comparison at download time is against the hex this app
    // produces, which is lowercase by construction (see Hex), so accepting either case here would
    // be the one place the two forms could meet and disagree.
    fun parse(raw: String?): String? = raw?.trim()
        ?.takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)
        ?.takeIf { it.length == HEX_LENGTH && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
}
