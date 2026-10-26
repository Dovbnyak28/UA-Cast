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
 * **A release of this app carries four APKs, not one**, and that is what this has to be right
 * about. `./gradlew :app:assembleRelease` produces `app-armeabi-v7a-release.apk`,
 * `app-arm64-v8a-release.apk`, `app-x86_64-release.apk` and `app-universal-release.apk` (see the
 * `splits` block and docs/RELEASING.md) - native code is ~78% of this app, so a per-ABI APK is less
 * than half the size of the universal one and publishing them together is the point.
 *
 * **The universal APK wins whenever it is there**, even though it is twice the download. That is
 * not a preference, it is forced by this project's own versionCode scheme: each per-ABI APK gets
 * `base × 10 + an ABI digit`, and universal deliberately takes the *highest* digit so that "every
 * other install must be able to move to it". A device currently on universal therefore cannot
 * install a per-ABI APK of the same release at all - Android refuses it as a downgrade, after the
 * download, in a system dialog. Saving 11MB is not worth an install that can be refused.
 *
 * Only when no universal APK was published does the device's own ABI decide, taking
 * `Build.SUPPORTED_ABIS` in its own order, which is most-preferred first. A per-ABI APK for the
 * wrong architecture fails at install with `INSTALL_FAILED_NO_MATCHING_ABIS`, so guessing here is
 * strictly worse than choosing.
 *
 * **What is still refused rather than guessed**: an ABI-labelled APK that does not match this
 * device, even when it is the release's only attachment. A single generic APK can still be used
 * because it does not claim an incompatible architecture. Refusing leaves the release page as the
 * offer, where a human can see what was published.
 */
object ReleaseApkPolicy {

    private const val APK_SUFFIX = ".apk"
    private const val STATE_UPLOADED = "uploaded"

    /** The name AGP gives the APK with no ABI filter - the one that runs everywhere. */
    private const val UNIVERSAL_MARKER = "universal"
    private val universalMarker = Regex("(?:^|[-_.])$UNIVERSAL_MARKER(?=[-_.]|$)", RegexOption.IGNORE_CASE)
    // Longest x86 variant first: x86 is not evidence that an x86_64-only APK is compatible.
    private val abiMarker = Regex("(?:^|[-_.])(arm64-v8a|armeabi-v7a|x86_64|x86)(?=[-_.]|$)", RegexOption.IGNORE_CASE)

    private fun labelledAbi(name: String): String? = abiMarker.find(name)?.groupValues?.get(1)

    private fun supports(asset: ReleaseAsset, abi: String): Boolean =
        labelledAbi(asset.name)?.equals(abi, ignoreCase = true) == true

    /**
     * @param supportedAbis this device's `Build.SUPPORTED_ABIS`, most-preferred first. Passed in
     *   rather than read here so this stays a pure rule with no Android behind it, the same shape
     *   every other policy in this package has.
     */
    fun pick(assets: List<ReleaseAsset>, supportedAbis: List<String> = emptyList()): ReleaseApk? {
        val candidates = assets.filter { asset ->
            asset.name.endsWith(APK_SUFFIX, ignoreCase = true) &&
                asset.state == STATE_UPLOADED &&
                asset.sizeBytes > 0 &&
                asset.downloadUrl.isNotBlank()
        }
        val chosen = candidates.firstOrNull {
            labelledAbi(it.name) == null && universalMarker.containsMatchIn(it.name)
        }
            ?: supportedAbis.firstNotNullOfOrNull { abi ->
                candidates.firstOrNull { supports(it, abi) }
            }
            ?: candidates.singleOrNull()?.takeIf { labelledAbi(it.name) == null }
            ?: return null
        return ReleaseApk(
            downloadUrl = chosen.downloadUrl,
            sizeBytes = chosen.sizeBytes,
            sha256 = ReleaseDigest.parse(chosen.digest),
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
