package com.uacastplayer

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Which settings a backup is allowed to carry to another device.
 *
 * Two of them - icon display mode and list density - are computed per device by
 * [com.uacastplayer.performance.DeviceTierDefaults], and stay computed until the user picks
 * something explicitly ([com.uacastplayer.data.prefs.AppPreferences.hasChosenIconDisplayMode] and
 * its list-density twin record that). So the settings state always carries a value for them,
 * whether anybody chose one or not, and the export took that value at face value.
 *
 * The result was a tier default travelling between devices as if it were a decision, in both
 * directions: a backup taken on a flagship pinned full icon rendering on a low-end phone the tier
 * logic exists to keep light, and one taken on a low-end phone pinned placeholders on a flagship.
 * And it could not be undone by using the app normally - the import goes through the same setters
 * a manual change would, so it makes hasChosen... true and the receiving device can never fall back
 * to its own default again.
 *
 * The rule was already there, two lines further down the same expression: the EPG fields are
 * exported only when `hasChosenEpgSource` says the user picked them.
 *
 * These are also the first tests [AppViewModel] has ever had. It builds under Robolectric without
 * complaint, and the backup is written through the real SAF path - a content Uri whose stream the
 * test owns.
 */
@RunWith(RobolectricTestRunner::class)
class BackupExportedSettingsTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://com.example.documents/tree/ua-cast-backup.json")

    /** Exports through the public SAF entry point and decodes what actually landed in the file. */
    private fun exportedSettings(viewModel: AppViewModel): BackupSettings {
        val written = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStream(uri, written)

        viewModel.exportBackupTo(uri)

        // The write hops to Dispatchers.IO, so wait for the bytes rather than assume them.
        val deadline = System.currentTimeMillis() + WRITE_WAIT_MILLIS
        while (System.currentTimeMillis() < deadline && written.size() == 0) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        val json = written.toString(Charsets.UTF_8.name())
        val decoded = BackupCodec.decode(json)
        assertNotNull("the backup should have been written and be readable", decoded)
        return decoded!!.settings
    }

    /**
     * The bug. Nothing has been chosen here, so the two tier-derived settings are whatever this
     * device computed - and that is not the user's to carry anywhere.
     */
    @Test
    fun `a backup does not export the settings this device chose on the user's behalf`() {
        val settings = exportedSettings(AppViewModel(application))

        assertNull("an unchosen icon display mode is this device's default, not a decision", settings.iconDisplayMode)
        assertNull("an unchosen list density is this device's default, not a decision", settings.listDensity)
        // This one used to be asserted non-null, on the stated grounds that buffer size "is not
        // tier-derived". It is derived now - from this app's own heap limit rather than from the
        // tier (see HeapBudget), after a 128MB device died of an OutOfMemoryError inside the video
        // decoder. So it belongs with the other two: exported unconditionally it would pin a 16MB
        // media buffer, chosen by a phone with room, onto the phone the smaller default exists for.
        assertNull(
            "an unchosen buffer size is this device's heap default, not a decision",
            settings.bufferSize,
        )
    }

    /**
     * The control, and what stops the above from being satisfied by an export that simply carries
     * nothing: a setting the user did pick travels, exactly as it always has.
     */
    @Test
    fun `a backup does export the settings the user picked`() {
        val viewModel = AppViewModel(application)
        viewModel.setIconDisplayMode(IconDisplayMode.PLACEHOLDERS)
        viewModel.setListDensity(ListDensity.FULL)

        val settings = exportedSettings(viewModel)

        assertEquals(IconDisplayMode.PLACEHOLDERS.name, settings.iconDisplayMode)
        assertEquals(ListDensity.FULL.name, settings.listDensity)
    }

    /** The same control for the setting that just joined them - a buffer size the user picked is
     * still a decision, and still travels. */
    @Test
    fun `a backup does export a buffer size the user picked`() {
        val viewModel = AppViewModel(application)
        viewModel.setBufferSize(BufferSize.LARGE)

        val settings = exportedSettings(viewModel)

        assertEquals(BufferSize.LARGE.name, settings.bufferSize)
    }

    private companion object {
        const val WRITE_WAIT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 20L
    }
}
