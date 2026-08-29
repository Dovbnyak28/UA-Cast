package com.uacastplayer.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.favorites.FavoritesRepository
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.PlaylistSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    private fun answerWith(target: Uri = uri, supplier: Supplier<InputStream>) {
        shadowOf(application.contentResolver).registerInputStreamSupplier(target, supplier)
    }

    /** The controller under test, reading and parsing on the test's own dispatcher. */
    private fun TestScope.controller() = BackupController(
        application = application,
        favoritesRepository = FavoritesRepository(application, backgroundScope),
        scope = this,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private class Import {
        var sources: List<PlaylistSource>? = null
        var settings: BackupSettings? = null
    }

    private fun aBackupWith(favoriteCount: Int, bufferSize: String = "LARGE"): String = buildString {
        append("""{"version":1,"sources":[],"favorites":[""")
        (1..favoriteCount).joinTo(this, separator = ",") { i ->
            """{"key":"k$i","displayName":"Channel $i","streamUrl":"http://example.test/$i",""" +
                """"addedAtMillis":$i}"""
        }
        append("""],"settings":{"bufferSize":"$bufferSize"}}""")
    }

    @Test
    fun `an ordinary backup is imported`() = runTest {
        answerWith { ByteArrayInputStream(aBackupWith(3).toByteArray()) }
        val seen = Import()
        val controller = controller()

        controller.importFrom(uri, { emptyList() }, { emptyList() }, { seen.sources = it }, { seen.settings = it })
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

        controller.importFrom(uri, { emptyList() }, { emptyList() }, { seen.sources = it }, { seen.settings = it })
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

        controller.importFrom(uri, { emptyList() }, { emptyList() }, { seen.sources = it }, { seen.settings = it })
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

        controller.importFrom(uri, { emptyList() }, { emptyList() }, { seen.sources = it }, { seen.settings = it })
        testScheduler.advanceUntilIdle()

        assertNull(seen.sources)
        assertNull(controller.backupImportSummary.value)
    }

    @Test
    fun `provider cancellation is not disguised as an unreadable backup`() = runTest {
        val expected = CancellationException("picker closed")
        answerWith {
            object : InputStream() {
                override fun read(): Int = throw expected
            }
        }

        val actual = assertThrows(CancellationException::class.java) {
            controller().readBoundedText(uri)
        }

        assertSame(expected, actual)
    }

    @Test
    fun `a slower older import cannot overwrite a newer selection`() {
        val firstUri = Uri.parse("content://com.example.documents/first.json")
        val secondUri = Uri.parse("content://com.example.documents/second.json")
        val firstReadStarted = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        answerWith(firstUri) {
            GatedInputStream(
                aBackupWith(1, bufferSize = "LARGE").toByteArray(),
                firstReadStarted,
                releaseFirstRead,
            )
        }
        answerWith(secondUri) {
            ByteArrayInputStream(aBackupWith(1, bufferSize = "SMALL").toByteArray())
        }
        val executor = Executors.newFixedThreadPool(2)
        val ioDispatcher = executor.asCoroutineDispatcher()
        val importScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val appliedBufferSizes = Collections.synchronizedList(mutableListOf<String>())
        val controller = BackupController(
            application = application,
            favoritesRepository = FavoritesRepository(application, importScope),
            scope = importScope,
            ioDispatcher = ioDispatcher,
        )
        try {
            val older = controller.importFrom(
                firstUri,
                { emptyList() },
                { emptyList() },
                {},
                { settings -> appliedBufferSizes += settings.bufferSize },
            )
            assertEquals(true, firstReadStarted.await(2, TimeUnit.SECONDS))
            val newer = controller.importFrom(
                secondUri,
                { emptyList() },
                { emptyList() },
                {},
                { settings -> appliedBufferSizes += settings.bufferSize },
            )

            runBlocking { newer.join() }
            releaseFirstRead.countDown()
            runBlocking { older.join() }

            assertEquals(listOf("SMALL"), appliedBufferSizes)
        } finally {
            releaseFirstRead.countDown()
            importScope.cancel()
            ioDispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a favorite added while a slow backup is read survives the merge`() {
        val firstReadStarted = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        answerWith {
            GatedInputStream(aBackupWith(1).toByteArray(), firstReadStarted, releaseFirstRead)
        }
        val executor = Executors.newFixedThreadPool(2)
        val ioDispatcher = executor.asCoroutineDispatcher()
        val importScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val favoritesRepository = FavoritesRepository(application, importScope)
        val liveFavorite = FavoriteChannel(
            key = "live-change",
            displayName = "Added while reading",
            streamUrl = "http://example.test/live",
            tvgId = "live-change",
            groupTitle = null,
            addedAtMillis = 2L,
        )
        var currentFavorites: List<FavoriteChannel> = emptyList()
        val controller = BackupController(application, favoritesRepository, importScope, ioDispatcher)
        try {
            val import = controller.importFrom(
                uri,
                { emptyList() },
                { currentFavorites },
                {},
                {},
            )
            assertTrue(firstReadStarted.await(2, TimeUnit.SECONDS))
            currentFavorites = listOf(liveFavorite)
            releaseFirstRead.countDown()
            runBlocking { import.join() }

            assertEquals(2, favoritesRepository.favorites.value.size)
            assertTrue(favoritesRepository.favorites.value.any { it.key == liveFavorite.key })
        } finally {
            releaseFirstRead.countDown()
            importScope.cancel()
            ioDispatcher.close()
            executor.shutdownNow()
        }
    }

    private class GatedInputStream(
        bytes: ByteArray,
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private val gated = AtomicBoolean()

        override fun read(): Int {
            awaitGate()
            return delegate.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            awaitGate()
            return delegate.read(bytes, offset, length)
        }

        private fun awaitGate() {
            if (gated.compareAndSet(false, true)) {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
            }
        }
    }
}
