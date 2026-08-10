package com.uacastplayer.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two ways to take the trial twice, both of which needed no root, no tooling and no skill.
 */
class TrialEligibilityPolicyTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun installedAgo(millis: Long) =
        TrialEligibilityPolicy.deservesTrial(firstInstallTimeMillis = now - millis, nowMillis = now)

    @Test
    fun arealFirstLaunchGetsTheTrial() {
        assertTrue("installed a moment ago", installedAgo(0))
        assertTrue("installed this morning", installedAgo(6 * 60 * 60 * 1000))
        assertTrue("day 13 of the trial, storage lost to a crash", installedAgo(13 * day))
    }

    /**
     * The regression this exists for: Settings -> Apps -> Storage -> Clear data empties
     * SharedPreferences on a stock, unrooted phone, and "storage is empty" was the entire test for
     * "this is a first launch". Three taps bought another fortnight, for as many fortnights as
     * anyone cared to spend them.
     *
     * `firstInstallTime` is what closes it: clearing data does not touch it, and only a real
     * uninstall resets it - which at least raises the price of the next round to a reinstall.
     */
    @Test
    fun anOldInstallWithEmptiedStorageDoesNotGetASecondTrial() {
        assertFalse("exactly the trial's length", installedAgo(License.TRIAL_DURATION_MILLIS))
        assertFalse("a fortnight and a day", installedAgo(15 * day))
        assertFalse("a year later", installedAgo(365 * day))
    }

    /**
     * Both ways of not knowing resolve towards granting. Refusing would deny a genuine first launch
     * to someone whose phone has the wrong date - which costs them the entire app - while granting
     * costs at most one extra fortnight.
     */
    @Test
    fun anUnknowableInstallAgeIsGivenTheBenefitOfTheDoubt() {
        assertTrue("the platform did not say", TrialEligibilityPolicy.deservesTrial(null, now))
        assertTrue("a clock behind the install date", installedAgo(-30 * day))
    }

    /**
     * Every expiry decision in this app is `now < expiresAt` against the system clock, and the
     * system clock is a setting. Turning off automatic date and time and winding it back made a
     * lapsed trial current again.
     */
    @Test
    fun aClockThatWentBackwardsIsIgnored() {
        val seen = now
        assertEquals(seen, TrialEligibilityPolicy.clampToHighWaterMark(now - 30 * day, seen))
        assertEquals(seen, TrialEligibilityPolicy.clampToHighWaterMark(0L, seen))
    }

    /** Time passing normally has to keep passing - the mark is a floor, not a freeze. */
    @Test
    fun timeMovingForwardIsAccepted() {
        assertEquals(now + day, TrialEligibilityPolicy.clampToHighWaterMark(now + day, now))
    }

    /**
     * Winding the clock *forward* is deliberately not resisted. It only ends an entitlement early,
     * which costs the user rather than the app - and it is what someone crossing a date line does.
     */
    @Test
    fun jumpingForwardAndBackLandsOnTheFurthestPointSeen() {
        val afterJump = TrialEligibilityPolicy.clampToHighWaterMark(now + 90 * day, now)
        assertEquals(now + 90 * day, afterJump)

        // ...and winding back afterwards buys nothing: the mark went with it.
        assertEquals(afterJump, TrialEligibilityPolicy.clampToHighWaterMark(now, afterJump))
    }

    /** Nothing recorded yet - every launch before the first entitlement is resolved. The system
     * clock is all there is, and it is taken at face value. */
    @Test
    fun withNoMarkRecordedTheSystemClockStands() {
        assertEquals(now, TrialEligibilityPolicy.clampToHighWaterMark(now, 0L))
    }
}
