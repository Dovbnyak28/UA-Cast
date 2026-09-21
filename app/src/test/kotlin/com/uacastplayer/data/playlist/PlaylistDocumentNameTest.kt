package com.uacastplayer.data.playlist

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class PlaylistDocumentNameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uri = Uri.parse("content://audit.documents/playlist")

    @Test fun `metadata lookup leaves Main and cancellation reaches provider`() = runTest {
        val provider = NameProvider()
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)
        val task = async { PlaylistFileLoader(context).documentName(uri) }
        testScheduler.runCurrent()
        try {
            assertTrue(provider.started.await(5, TimeUnit.SECONDS))
            assertNotEquals(Looper.getMainLooper().thread, provider.thread)
            task.cancel()
            assertTrue(provider.cancelled.await(5, TimeUnit.SECONDS))
        } finally {
            provider.release.countDown()
        }
        task.join()
        assertTrue(task.isCancelled)
    }

    @Test fun `ordinary metadata returns provider name and closes cursor`() = runTest {
        val provider = NameProvider().apply { release.countDown() }
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)
        assertEquals("Мій плейлист.m3u8", PlaylistFileLoader(context).documentName(uri))
        assertTrue(provider.cursor.isClosed)
    }

    @Test fun `missing provider metadata is optional rather than fatal`() = runTest {
        assertEquals(null, PlaylistFileLoader(context).documentName(uri))
    }

    private class NameProvider : ContentProvider() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        @Volatile var thread: Thread? = null
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("Мій плейлист.m3u8")) }
        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?,
        ): Cursor {
            thread = Thread.currentThread()
            cancellationSignal?.setOnCancelListener { cancelled.countDown(); release.countDown() }
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            return cursor
        }
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor = error("Use the cancellable metadata query")
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
