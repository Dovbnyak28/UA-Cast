package com.uacastplayer.update

/**
 * Whether a downloaded APK was signed by whoever signed the copy already installed.
 *
 * This is the check that actually decides anything. A hash says the bytes are the bytes the release
 * described; it says nothing about who wrote the release. Android will refuse an install whose
 * signer does not match - that is what makes an update an update rather than a different app - but
 * it refuses it *after* the download, inside a system dialog, with an error the user cannot read
 * anything out of. Asking first turns that into a sentence the app can say.
 *
 * It also draws the line in the one place it matters for this project's own delivery: an APK
 * downloaded over the network, from a repository that is public, onto a device that already holds
 * the user's playlist and their purchase. A signer mismatch there is not a curiosity to report and
 * carry on from - it is the point at which the file stops being an update.
 *
 * **Set equality, not overlap, and deliberately strict about one case.** An app signed by a
 * rotated key reports its *current* signer, on both sides, so an ordinary update compares equal.
 * A key rotation itself would not: the new APK would carry the new signer while the installed copy
 * still carries the old, and this refuses it. That is the safe direction and the honest one - a
 * rotation is a thing to do deliberately, through a release note and a manual install, not
 * something an automatic updater should wave through because the certificates were "related".
 *
 * Empty on either side is refused rather than treated as "nothing to compare". An APK whose
 * signature could not be read is not one to install, and an installed copy with no readable signer
 * means the question cannot be answered at all.
 */
object ApkTrustPolicy {

    /** [installed] and [candidate] are the SHA-256 digests of each side's signing certificates. */
    fun isSameSigner(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && installed == candidate
}
