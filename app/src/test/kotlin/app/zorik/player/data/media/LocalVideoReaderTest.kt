package app.zorik.player.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.data.playlist.PlaylistFileLoader
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class LocalVideoReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://audit.video/document")
    private lateinit var provider: VideoProvider
    private lateinit var file: File

    @Before fun register() {
        file = File.createTempFile("metadata-video", ".mp4", context.cacheDir).apply { writeBytes(ByteArray(32)) }
        provider = VideoProvider(file)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "audit.video"
            exported = true
        })
        ShadowContentResolver.registerProviderInternal("audit.video", provider)
    }

    @After fun cleanUp() { provider.release.countDown(); file.delete() }

    @Test fun readableVideoMetadataClosesTheCursorAndDescriptorOffMain() = runBlocking {
        provider.release.countDown()
        val result = AndroidLocalVideoReader(context).read(uri.toString(), persist = false)
        assertEquals("Movie.mp4", result.displayName)
        assertEquals("video/mp4", result.mimeType)
        assertFalse(result.canRestore)
        assertNotEquals(Looper.getMainLooper().thread, provider.queryThread)
        assertTrue(provider.cursor.isClosed)
        assertEquals(1, provider.opens.get())
        assertTrue("The metadata descriptor must close", checkNotNull(provider.descriptor).fileDescriptor.valid().not())
    }

    @Test fun cancelledMetadataCannotStartAnotherProviderRequest() = runBlocking {
        cancelRead { AndroidLocalVideoReader(context).read(uri.toString(), persist = false) }
        assertEquals("No MIME lookup after query cancellation", 0, provider.typeQueries.get())
        assertEquals(0, provider.opens.get())
        assertTrue("A late cursor must still close", provider.cursor.isClosed)
    }

    @Test fun aThrowingProviderCancellationCannotEscapeTheVideoRead() = runBlocking {
        provider.throwOnCancel = true
        val errors = cancelRead { AndroidLocalVideoReader(context).read(uri.toString(), persist = false) }
        assertTrue("Provider cancellation must not become an unhandled coroutine error: $errors", errors.isEmpty())
    }

    @Test fun aThrowingProviderCancellationCannotEscapeThePlaylistNameRead() = runBlocking {
        provider.throwOnCancel = true
        val errors = cancelRead { PlaylistFileLoader(context).documentName(uri) }
        assertTrue("Playlist metadata cancellation must stay inside its boundary: $errors", errors.isEmpty())
    }

    @Test fun aProviderCancellationExceptionCannotEscapeTheVideoCancellationHandler() = runBlocking {
        provider.cancellationFailure = CancellationException("Provider callback failed")
        val errors = cancelRead { AndroidLocalVideoReader(context).read(uri.toString(), persist = false) }
        assertTrue("Provider exceptions must not escape an already cancelled Job: $errors", errors.isEmpty())
    }

    @Test fun aProviderCancellationExceptionCannotEscapeThePlaylistCancellationHandler() = runBlocking {
        provider.cancellationFailure = CancellationException("Provider callback failed")
        val errors = cancelRead { PlaylistFileLoader(context).documentName(uri) }
        assertTrue("Provider cancellation must not become a completion handler failure: $errors", errors.isEmpty())
    }

    private suspend fun cancelRead(read: suspend () -> Unit): List<Throwable> {
        val errors = ConcurrentLinkedQueue<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            errors.add(error)
        })
        val task = scope.launch { read() }
        try {
            assertTrue("Metadata query must be in flight", provider.started.await(5, TimeUnit.SECONDS))
            task.cancel()
            withTimeout(5_000) { task.join() }
            assertTrue(task.isCancelled)
            assertTrue(provider.cancelled.await(5, TimeUnit.SECONDS))
            return errors.toList()
        } finally {
            provider.release.countDown()
            task.cancel()
            withTimeout(5_000) { task.join() }
            scope.cancel()
        }
    }

    private class VideoProvider(private val file: File) : ContentProvider() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val typeQueries = AtomicInteger()
        val opens = AtomicInteger()
        var throwOnCancel = false
        var cancellationFailure: RuntimeException? = null
        @Volatile var queryThread: Thread? = null
        @Volatile var descriptor: ParcelFileDescriptor? = null
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("Movie.mp4")) }
        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?,
        ): Cursor {
            queryThread = Thread.currentThread()
            cancellationSignal?.setOnCancelListener {
                cancelled.countDown()
                release.countDown()
                cancellationFailure?.let { throw it }
                if (throwOnCancel) throw IllegalStateException("Broken provider cancellation")
            }
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            return cursor
        }
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor = error("Use the cancellable query")
        override fun getType(uri: Uri): String { typeQueries.incrementAndGet(); return "video/mp4" }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opens.incrementAndGet()
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also { descriptor = it }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
