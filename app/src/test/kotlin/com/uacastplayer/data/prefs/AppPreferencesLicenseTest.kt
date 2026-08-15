package com.uacastplayer.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.LicenseIntegrity
import com.uacastplayer.core.security.LicenseRecordCodec
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Storing a licence and getting the same licence back.
 *
 * The bug: on a device whose Keystore cannot produce a MAC, [AppPreferences.storedLicense] wrote a
 * record with an empty tag - deliberately, so the licence would survive a Keystore that will not
 * co-operate - and then read that record back as [License.FREE], because the codec refused to parse
 * an empty tag at all. A paid LIFETIME went in and the free tier came out, on the next read.
 *
 * It also cost the trial. A stored licence is what tells `PremiumRepository` this install has had
 * its fortnight, and an unreadable record still resolves to a non-null free licence - so a first
 * launch on such a device granted the trial, stored it, and the next launch found the free tier and
 * never granted it again. One session out of fourteen days.
 *
 * **The environment fact that makes this testable: Robolectric has no AndroidKeyStore**, so
 * [LicenseIntegrity.isAvailable] is false here and every write goes down the untagged path by
 * itself. That is not a shortcoming of the harness - it is a faithful stand-in for the device this
 * bug is about. The opposite half, a device that *can* tag, is reached through the constructor's
 * `canTagLicense` seam, since no test on any harness can make a real Keystore appear.
 */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesLicenseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun preferences(canTag: Boolean) = AppPreferences(context, canTagLicense = { canTag })

    private val lifetime = License(LicenseTier.LIFETIME, expiresAtMillis = null, source = "premium_lifetime")

    /** The environment this whole file rests on, asserted rather than assumed. */
    @Test
    fun `this harness cannot tag, which is the device the bug is about`() {
        assertFalse(LicenseIntegrity.isAvailable())
        assertNull(LicenseIntegrity.sign("anything"))
    }

    /** The bug itself. */
    @Test
    fun `a licence written on a device that cannot tag survives being read back`() {
        val prefs = preferences(canTag = false)

        prefs.storedLicense = lifetime

        assertEquals(lifetime, prefs.storedLicense)
    }

    /**
     * The trial's side of the same bug, at the level the repository asks its question: a stored
     * licence must come back as the licence stored, or the fortnight is spent in one session.
     */
    @Test
    fun `a trial written on a device that cannot tag is still a trial next launch`() {
        val prefs = preferences(canTag = false)
        val trial = License.trialStartingAt(1_000_000L)

        prefs.storedLicense = trial

        assertEquals(trial, prefs.storedLicense)
        assertEquals(trial.expiresAtMillis, prefs.storedLicense?.expiresAtMillis)
    }

    /**
     * The other half, and the reason the empty tag is not simply trusted. Stripping everything
     * after the last separator is the cheapest edit there is, and on a device that can tag it must
     * buy nothing. Written through the codec rather than by hand so the record is genuinely the
     * shape the reader meets, not a guess at it.
     */
    @Test
    fun `a tag stripped by hand buys nothing on a device that can tag`() {
        preferences(canTag = false).storedLicense = lifetime
        val onCapableDevice = preferences(canTag = true)

        assertEquals(License.FREE, onCapableDevice.storedLicense)
    }

    /**
     * The control for the test above: it must fail because the tag is missing, not because a
     * capable device rejects every record it reads. A tag that is present but wrong is rejected by
     * [LicenseIntegrity.verify], which is a different line - this pins that the empty-tag branch is
     * what the previous test exercises.
     */
    @Test
    fun `a wrong tag is refused too, and by a different line`() {
        val forged = LicenseRecordCodec.encode(
            LicenseRecordCodec.payload(LicenseTier.LIFETIME.name, null, "premium_lifetime"),
            mac = "0000deadbeef",
        )
        assertEquals("the tag here is present and wrong", "0000deadbeef", LicenseRecordCodec.decode(forged)!!.second)

        context.getSharedPreferences("uacast_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("license_record", forged)
            .commit()

        assertEquals(License.FREE, preferences(canTag = true).storedLicense)
        assertEquals(
            "and refused on a device that cannot tag either - it is a wrong tag, not a missing one",
            License.FREE,
            preferences(canTag = false).storedLicense,
        )
    }

    /** Nothing stored is still nothing stored - the one state that earns a first-launch trial. */
    @Test
    fun `an empty store stays null so the trial can still be granted`() {
        assertNull(preferences(canTag = false).storedLicense)
        assertNull(preferences(canTag = true).storedLicense)
    }

    /** Clearing must clear on this path too, or a cancelled purchase would linger untagged. */
    @Test
    fun `clearing a licence written with no tag leaves nothing behind`() {
        val prefs = preferences(canTag = false)
        prefs.storedLicense = lifetime

        prefs.storedLicense = null

        assertNull(prefs.storedLicense)
    }
}
