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
 * **What is still refused rather than guessed**: several APKs, none universal, none matching this
 * device. That is a release built for architectures this phone is not, or a debug build published
 * beside a release one - and an APK signed with the debug key cannot install over a release-signed
 * app under any circumstances. Refusing leaves the release page as the offer, where a human can
 * see what the files are.
 */
object ReleaseApkPolicy {

    private const val APK_SUFFIX = ".apk"
    private const val STATE_UPLOADED = "uploaded"

    /** The name AGP gives the APK with no ABI filter - the one that runs everywhere. */
    private const val UNIVERSAL_MARKER = "universal"

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
        val chosen = candidates.singleOrNull()
            ?: candidates.firstOrNull { it.name.contains(UNIVERSAL_MARKER, ignoreCase = true) }
            ?: supportedAbis.firstNotNullOfOrNull { abi ->
                candidates.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            }
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
