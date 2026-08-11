package com.uacastplayer.core.security

import com.uacastplayer.premium.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The format a stored licence is authenticated in.
 *
 * The tag is only worth as much as the payload it covers, so these are about the payload: two
 * different licences must never agree on one, and nothing a user can put into a licence may move
 * the boundary between the payload and the tag.
 */
class LicenseRecordCodecTest {

    private fun payload(tier: LicenseTier, expiry: Long? = null, source: String? = null) =
        LicenseRecordCodec.payload(tier.name, expiry, source)

    /**
     * The swap this format exists to prevent. If "no expiry" and "expiry removed" produced the same
     * payload, a lifetime tag would authenticate a subscription that had run out - which is the
     * upgrade an attacker would want most.
     */
    @Test
    fun noExpiryAndAnExpiryAreNeverTheSamePayload() {
        assertNotEquals(payload(LicenseTier.LIFETIME), payload(LicenseTier.LIFETIME, expiry = 0L))
        assertNotEquals(payload(LicenseTier.MONTHLY, expiry = 1L), payload(LicenseTier.MONTHLY))
    }

    /** Every field that decides access is covered, so changing any one of them invalidates the tag. */
    @Test
    fun everyFieldThatDecidesAccessChangesThePayload() {
        val base = payload(LicenseTier.MONTHLY, expiry = 100L, source = "premium_monthly")

        assertNotEquals("tier", base, payload(LicenseTier.LIFETIME, expiry = 100L, source = "premium_monthly"))
        assertNotEquals("expiry", base, payload(LicenseTier.MONTHLY, expiry = 200L, source = "premium_monthly"))
        assertNotEquals("source", base, payload(LicenseTier.MONTHLY, expiry = 100L, source = "premium_yearly"))
    }

    /**
     * The separator attack. `source` is a product id today, but it is a string that has held a
     * store's own text before - and a naive split on every separator would let a chosen value
     * shift where the tag begins, so a record could carry its own boundary.
     */
    @Test
    fun aSeparatorInsideTheSourceCannotMoveTheTag() {
        val awkward = payload(LicenseTier.LIFETIME, expiry = null, source = "premium|lifetime|x")

        val (decodedPayload, mac) = LicenseRecordCodec.decode(LicenseRecordCodec.encode(awkward, "deadbeef"))!!

        assertEquals(awkward, decodedPayload)
        assertEquals("deadbeef", mac)
    }

    @Test
    fun aRecordRoundTrips() {
        val text = LicenseRecordCodec.encode(payload(LicenseTier.YEARLY, 42L, "premium_yearly"), "abc123")

        val (decodedPayload, mac) = LicenseRecordCodec.decode(text)!!

        assertEquals(payload(LicenseTier.YEARLY, 42L, "premium_yearly"), decodedPayload)
        assertEquals("abc123", mac)
    }

    /**
     * Anything that is not a record at all. The caller answers these with the free tier, so what
     * matters here is only that they are recognisable as not-a-record rather than parsed into one.
     */
    @Test
    fun rubbishIsNotMistakenForARecord() {
        assertNull("empty", LicenseRecordCodec.decode(""))
        assertNull("no separator", LicenseRecordCodec.decode("LIFETIME"))
        assertNull("nothing before the tag", LicenseRecordCodec.decode("|abc"))
        assertNull("nothing after the payload", LicenseRecordCodec.decode("LIFETIME|-|-|"))
    }
}
