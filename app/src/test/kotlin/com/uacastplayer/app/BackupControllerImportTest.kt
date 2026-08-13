package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.playlist.PlaylistSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.Supplier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What the backup import does with a file that is not the backup it was promised.
 *
 * The import reads a file the user picked through SAF, and until now it read it with `readText()` -
 * no bound at all. Every other stream in this app is bounded, including
 * [com.uacastplayer.data.playlist.PlaylistFileLoader], the *other* SAF-picked file, whose own test
 * says why: "the cap exists so a wrong pick - a video, a disk image - cannot be pulled into memory".
 * The picker offers every file on the device and the wrong one is one tap away.
 *
 * The second thing this pins is where the work happens. `importFrom` parsed the JSON wherever
 * `scope.launch` left it, and that scope is `viewModelScope` - `Dispatchers.Main.immediate` - so
 * the parse ran on the frame-drawing thread. The dispatcher is injected here for the same reason
 * [ParentalControlController]'s is, and these tests drive it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupControllerImportTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://com.example.documents/tree/ua-cast-backup.json")

    private fun answerWith(supplier: Supplier<InputStream>) {
        shadowOf(application.contentResolver).registerInputStreamSupplier(uri, supplier)
    }

    /** The controller under test, reading and parsing on the test's own dispatcher. */
    private fun TestScope.controller() = BackupController(
        application = application,
        favoritesRepository = FavoritesRepository(application),
        scope = this,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private class Import {
        var sources: List<PlaylistSource>? = null
        var settings: BackupSettings? = null
    }

    private fun aBackupWith(favoriteCount: Int): String = buildString {
        append("""{"version":1,"sources":[],"favorites":[""")
        (1..favoriteCount).joinTo(this, separator = ",") { i ->
            """{"key":"k$i","displayName":"Channel $i","streamUrl":"http://example.test/$i",""" +
                """"addedAtMillis":$i}"""
        }
        append("""],"settings":{"bufferSize":"LARGE"}}""")
    }

    @Test
    fun `an ordinary backup is imported`() = runTest {
        answerWith { ByteArrayInputStream(aBackupWith(3).toByteArray()) }
        val seen = Import()
        val controller = controller()

        controller.importFrom(uri, emptyList(), emptyList(), { seen.sources = it }, { seen.settings = it })
        testScheduler.advanceUntilIdle()

        assertNotNull("the import should have reached the callbacks", seen.sources)
        assertEquals("LARGE", seen.settings?.bufferSize)
        assertEquals(3, controller.backupImportSummary.value?.importedFavoriteCount)
    }

    /**
     * The wrong pick. Refused without being read into memory, and - like every other unusable file
     * here - silently: no summary, no callbacks, nothing changed.
     */
    @Test
    fun `a file over the size cap is refused rather than read`() = runTest {
        answerWith { ByteArrayInputStream(ByteArray(BackupController.MAX_BACKUP_BYTES + 1)) }
        val seen = Import()
        val controller = controller()

        controller.importFrom(uri, emptyList(), emptyList(), { seen.sources = it }, { seen.settings = it })
        testScheduler.advanceUntilIdle()

        assertNull("nothing may be merged from a file this big", seen.sources)
        assertNull(seen.settings)
        assertNull(controller.backupImportSummary.value)
    }

    /** A file exactly at the cap is still a backup - the bound must refuse what is over it, not
     * what reaches it. */
    @Test
    fun `a file exactly at the cap is still read`() = runTest {
        val padded = aBackupWith(1).let { it + " ".repeat(BackupController.MAX_BACKUP_BYTES - it.length) }
        answerWith { ByteArrayInputStream(padded.toByteArray()) }
        val seen = Import()
        val controller = controller()

        controller.importFrom(uri, emptyList(), emptyList(), { seen.sources = it }, { seen.settings = it })
        testScheduler.advanceUntilIdle()

        assertNotNull("a file at exactly the cap is within it", seen.sources)
    }

    /** A provider that dies mid-read - a dying SD card, a network provider that dropped. The import
     * is a no-op; it is not a crash inside viewModelScope. */
    @Test
    fun `a read that fails part way changes nothing`() = runTest {
        answerWith {
            object : InputStream() {
                private var served = 0
                override fun read(): Int = if (served++ < 10) '{'.code else throw IOException("I/O error")
            }
        }
        val seen = Import()
        val controller = controller()

        controller.importFrom(uri, emptyList(), emptyList(), { seen.sources = it }, { seen.settings = it })
        testScheduler.advanceUntilIdle()

        assertNull(seen.sources)
        assertNull(controller.backupImportSummary.value)
    }
}
