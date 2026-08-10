package com.uacastplayer.guidedtour

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the tour opens without being asked.
 *
 * Two of these four cases are the whole feature behaving itself. A tour that reopens after being
 * skipped has stopped asking and started nagging; one that never reopens after a new edition ships
 * has quietly become dead code.
 */
class GuidedTourAvailabilityTest {

    @Test
    fun aDeviceThatHasNeverSeenItIsOfferedIt() {
        assertTrue(
            GuidedTourAvailability.shouldOfferAutomatically(
                completed = false,
                seenVersion = 0,
                currentVersion = 1,
            ),
        )
    }

    @Test
    fun aDeviceThatHasSeenThisEditionIsLeftAlone() {
        assertFalse(
            GuidedTourAvailability.shouldOfferAutomatically(
                completed = true,
                seenVersion = 1,
                currentVersion = 1,
            ),
        )
    }

    @Test
    fun anOlderEditionIsOfferedAgain() {
        assertTrue(
            GuidedTourAvailability.shouldOfferAutomatically(
                completed = true,
                seenVersion = 1,
                currentVersion = 2,
            ),
        )
    }

    /**
     * The upgrade case for a device that predates the feature: the flag reads false and the version
     * reads 0, because that is what SharedPreferences returns for keys that were never written. It
     * must land on "offer it", not on some third behaviour.
     */
    @Test
    fun aDeviceFromBeforeTheFeatureExistedReadsAsNeverSeen() {
        assertTrue(
            GuidedTourAvailability.shouldOfferAutomatically(
                completed = false,
                seenVersion = 0,
                currentVersion = GuidedTourVersion.CURRENT,
            ),
        )
    }

    /**
     * A stored version *ahead* of this build - a downgrade, or a restored backup from a newer
     * release - is not a reason to show the tour again. The user has seen at least this much.
     */
    @Test
    fun aVersionFromTheFutureIsNotTreatedAsUnseen() {
        assertFalse(
            GuidedTourAvailability.shouldOfferAutomatically(
                completed = true,
                seenVersion = 5,
                currentVersion = 2,
            ),
        )
    }
}
