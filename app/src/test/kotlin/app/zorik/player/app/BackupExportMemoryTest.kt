package app.zorik.player.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.backup.BackupData
import app.zorik.player.backup.BackupFavorite
import app.zorik.player.backup.BackupSettings
import app.zorik.player.data.favorites.FavoritesRepository
import app.zorik.player.testsupport.JvmAllocations
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupExportMemoryTest {
    @Test fun oversizedFavoritesStopEncodingWithinTheAllocationBudgetBeforeOpeningTheDocument() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("content://fixture/oversized-backup.json")
        var opened = 0
        shadowOf(application.contentResolver).registerOutputStreamSupplier(uri) {
            opened++
            ByteArrayOutputStream()
        }
        val controller = BackupController(application, FavoritesRepository(application, backgroundScope), this)
        val empty = BackupData(emptyList(), emptyList(), BackupSettings())
        assertTrue(controller.writeBackup(uri, empty)) // Warm up JSON/framework classes before measuring.
        opened = 0
        val url = "https://provider.test/live?token=" + "x".repeat(8192)
        val favorites = List(4_000) { index ->
            BackupFavorite("key-$index", "Channel $index", url, "id-$index", null, 1)
        }
        val data = empty.copy(favorites = favorites)
        val before = JvmAllocations.currentThreadBytes()
        assertTrue("The allocation counter must be available", before >= 0)
        val exported = controller.writeBackup(uri, data)
        val allocated = JvmAllocations.currentThreadBytes() - before
        println("BACKUP_OVERSIZE allocatedBytes=$allocated rows=${favorites.size}")
        assertFalse(exported)
        assertEquals("An oversized backup must not truncate the selected document", 0, opened)
        assertTrue("Oversize refusal allocated $allocated bytes, budget is 64 MiB", allocated < 64L * 1024 * 1024)
    }
}
