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
 * Multi-signer APKs require exact current-signer equality. A single-signer APK may rotate its key:
 * Android verifies the proof-of-rotation embedded in the candidate and exposes the authenticated
 * chain through `SigningInfo.signingCertificateHistory`. The old installed signer must occur in
 * that chain; mere overlap between two unauthenticated sets is never enough.
 *
 * Empty on either side is refused rather than treated as "nothing to compare". An APK whose
 * signature could not be read is not one to install, and an installed copy with no readable signer
 * means the question cannot be answered at all.
 */
object ApkTrustPolicy {

    /** All values are SHA-256 digests of signing certificates. [candidateHistory] must be the
     * platform-verified proof-of-rotation history, not a list assembled from untrusted metadata. */
    fun isTrustedUpdate(
        installedCurrent: Set<String>,
        candidateCurrent: Set<String>,
        candidateHistory: Set<String>,
    ): Boolean = installedCurrent.isNotEmpty() && candidateCurrent.isNotEmpty() &&
        (installedCurrent == candidateCurrent ||
            (installedCurrent.size == 1 && candidateCurrent.size == 1 &&
                candidateHistory.containsAll(installedCurrent) &&
                candidateHistory.containsAll(candidateCurrent)))
}
