package com.uacastplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate an automatic update has to get through.
 *
 * A published hash says the bytes are the bytes the release described. It says nothing about who
 * wrote the release, and it is the second question that decides whether a file downloaded off the
 * network may be installed over an app that already holds the user's playlist and their purchase.
 */
class ApkTrustPolicyTest {

    private val ours = setOf("aa11")
    private val theirs = setOf("bb22")

    @Test
    fun `the same signer is an update`() {
        assertTrue(ApkTrustPolicy.isTrustedUpdate(ours, ours, ours))
    }

    @Test
    fun `a different signer is not`() {
        assertFalse(ApkTrustPolicy.isTrustedUpdate(ours, theirs, theirs))
    }

    /**
     * Both directions of "could not tell". An APK whose signature would not parse must not be the
     * easiest one to get past a gate, and an installed copy with no readable signer means the
     * question cannot be answered rather than that the answer is yes.
     */
    @Test
    fun `an unreadable signature is refused, not waved through`() {
        assertFalse("nothing in the file", ApkTrustPolicy.isTrustedUpdate(ours, emptySet(), emptySet()))
        assertFalse("nothing installed", ApkTrustPolicy.isTrustedUpdate(emptySet(), ours, ours))
        assertFalse("nothing either side", ApkTrustPolicy.isTrustedUpdate(emptySet(), emptySet(), emptySet()))
    }

    /**
     * Set equality rather than overlap. An APK carrying an extra signer beside ours is not one this
     * app signed - it is one somebody else also signed, which is a different thing entirely and the
     * exact shape an "any of them matches" rule would accept.
     */
    @Test
    fun `an extra signer beside ours is still someone else`() {
        assertFalse(ApkTrustPolicy.isTrustedUpdate(ours, ours + theirs, ours + theirs))
        assertFalse(ApkTrustPolicy.isTrustedUpdate(ours + theirs, ours, ours + theirs))
    }

    /** Order is not part of the answer - two signers are the same two whichever way they were read
     * out of the archive. */
    @Test
    fun `multiple signers match regardless of the order they were read in`() {
        assertTrue(
            ApkTrustPolicy.isTrustedUpdate(
                setOf("aa11", "bb22"),
                setOf("bb22", "aa11"),
                setOf("bb22", "aa11"),
            ),
        )
    }

    @Test
    fun `a platform verified key rotation is an update`() {
        assertTrue(ApkTrustPolicy.isTrustedUpdate(ours, theirs, ours + theirs))
    }

    @Test
    fun `a new signer without the installed signer in its lineage is refused`() {
        assertFalse(ApkTrustPolicy.isTrustedUpdate(ours, theirs, theirs))
    }

    @Test
    fun `rotation never relaxes multi signer equality`() {
        assertFalse(ApkTrustPolicy.isTrustedUpdate(ours + theirs, setOf("cc33"), ours + theirs + "cc33"))
    }
}
