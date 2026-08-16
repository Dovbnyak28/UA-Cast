package com.uacastplayer.ui.diagnostics

import android.content.Intent
import android.net.Uri
import com.uacastplayer.diagnostics.DiagnosticsEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    /** The shape `DiagnosticsArchive` hands back: the app's own FileProvider authority over the one
     * cache directory `res/xml/diagnostics_paths.xml` exposes. Verbatim from the ZTE log. */
    private val attachment: Uri =
        Uri.parse("content://com.uacastplayer.debug.diagnostics/diagnostics/ua-cast-log-20260816-162410.txt")

    /**
     * The attachment has to be reachable by the chooser, not only by the app the user picks.
     *
     * `EXTRA_STREAM` plus `FLAG_GRANT_READ_URI_PERMISSION` is enough for the *target* - which is why
     * this was invisible: the mail app opened with the file attached and the send worked. The chooser
     * is a different process (uid 1000) that reads the URI before anything has been picked, to show
     * the file's name and thumbnail, and the grant does not reach it through an extra. `clipData` is
     * the field `Intent.createChooser` documents for exactly this, and a ZTE Blade A34 log carried
     * both the `SecurityException` from the denied read and the framework's own warning naming it.
     *
     * What can be asserted here is the intent; the system chooser's read is not something a unit
     * test can perform. Remove the `clipData` line in [diagnosticsEmailIntentWithLog] and this test
     * fails while every other test in this class still passes.
     */
    @Test
    fun theChooserCanReadTheAttachmentItIsAskedToPreview() {
        val intent = diagnosticsEmailIntentWithLog(report, attachment)

        val clip = intent.clipData
        assertNotNull("The chooser previews clipData, not EXTRA_STREAM", clip)
        assertEquals(1, clip!!.itemCount)
        // The same file, not merely some URI: a clip pointing anywhere else would satisfy a
        // null-check and still leave the preview reading a URI it has no grant for.
        assertEquals(attachment, clip.getItemAt(0).uri)
        assertEquals(attachment, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    /** The grant flag is what the clip data carries; without it the clip is a URI nobody may open. */
    @Test
    fun theAttachmentIsSentWithAReadGrant() {
        val intent = diagnosticsEmailIntentWithLog(report, attachment)

        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    /** Control: the summary-only intent carries no stream at all, so it needs no clip and must not
     * grow one - a mail composer is not a share sheet. */
    @Test
    fun theSummaryOnlyEmailAttachesNothing() {
        val intent = diagnosticsEmailIntent(report)

        assertEquals(null, intent.clipData)
        assertEquals(null, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }
}
