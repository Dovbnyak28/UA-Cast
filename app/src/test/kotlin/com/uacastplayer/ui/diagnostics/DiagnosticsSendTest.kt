package com.uacastplayer.ui.diagnostics

import android.content.Intent
import com.uacastplayer.diagnostics.DiagnosticsEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The email a diagnostics report is handed to.
 *
 * Asserted because every part of it is silent when wrong: an address that never made it into the
 * extras produces a perfectly working mail composer that the user then has to address themselves,
 * and nobody reports "the button worked but I had to type your address" - they just close it.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsSendTest {

    private val report = "UA Cast diagnostics report\nApp version: 0.9.0\n[DEBUG] Player: started"

    @Test
    fun theReportIsAddressedToTheDeveloper() {
        val intent = diagnosticsEmailIntent(report)

        val addressed = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList()

        assertEquals(listOf(DiagnosticsEmail.RECIPIENT), addressed)
    }

    /**
     * `ACTION_SENDTO` over a `mailto:` URI, not `ACTION_SEND`.
     *
     * `ACTION_SEND` offers every messenger, notes app and cloud drive on the device, none of which
     * can deliver this anywhere useful and each of which is one mis-tap away from posting a device
     * log into a group chat. This is the line that keeps the picker to mail apps.
     */
    @Test
    fun onlyMailAppsCanAnswerIt() {
        val intent = diagnosticsEmailIntent(report)

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
    }

    @Test
    fun theWholeReportIsTheBody() {
        assertEquals(report, diagnosticsEmailIntent(report).getStringExtra(Intent.EXTRA_TEXT))
    }

    /** The subject is what lets an inbox be read as a list without opening anything. */
    @Test
    fun theSubjectCarriesTheVersionAndTheDevice() {
        val subject = DiagnosticsEmail.subject(appVersionName = "0.9.0", deviceModel = "Mi A2")

        assertEquals("UA Cast 0.9.0 - Mi A2", subject)
        assertTrue(diagnosticsEmailIntent(report).getStringExtra(Intent.EXTRA_SUBJECT)!!.startsWith("UA Cast "))
    }

    /**
     * The address is a constant compiled into the APK, so it is public to anyone who decompiles a
     * release. Pinned here so that swapping it is a deliberate act with a test to update, rather
     * than a one-character edit nobody reviews - and so a personal address cannot drift in by
     * accident later.
     */
    @Test
    fun theRecipientIsTheDedicatedAddress() {
        assertEquals("dovbnyak@hotmail.com", DiagnosticsEmail.RECIPIENT)
    }
}
