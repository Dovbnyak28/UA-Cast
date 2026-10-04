package com.uacastplayer.premium

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseClockPolicyTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun clockRollbackCannotReactivateExpiredAccess() {
        assertEquals(now, LicenseClockPolicy.clampToHighWaterMark(now - 30 * day, now))
        assertEquals(now, LicenseClockPolicy.clampToHighWaterMark(0L, now))
    }

    @Test
    fun forwardTimeIsAccepted() {
        assertEquals(now + day, LicenseClockPolicy.clampToHighWaterMark(now + day, now))
    }

    @Test
    fun returningFromAForwardJumpKeepsTheLatestObservedTime() {
        val afterJump = LicenseClockPolicy.clampToHighWaterMark(now + 90 * day, now)
        assertEquals(now + 90 * day, afterJump)
        assertEquals(afterJump, LicenseClockPolicy.clampToHighWaterMark(now, afterJump))
    }

    @Test
    fun anEmptyClockRecordUsesSystemTime() {
        assertEquals(now, LicenseClockPolicy.clampToHighWaterMark(now, 0L))
    }
}
