package com.uacastplayer.data.playlist

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.PlaylistLoadResult
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.function.Supplier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Every way opening a picked file can fail, and the rule all of them answer to: **no state of the
 * filesystem may crash this app.**
 *
 * The loader's job is to turn all of it into one [PlaylistLoadResult.ReadError], because the layer
 * above runs inside `viewModelScope` with no exception handler - anything that escapes here is not
 * an error message, it is the process ending.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistFileLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://com.example.documents/tree/playlist.m3u")

    private fun answerWith(supplier: Supplier<InputStream>) {
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri, supplier)
    }

    private suspend fun load(): PlaylistLoadResult = PlaylistFileLoader(context).load(uri)

    @Test
    fun anOrdinaryFileIsRead() = runTest {
        val m3u = "#EXTM3U\n#EXTINF:-1,Перший\nhttps://example/1.m3u8\n"
        answerWith { ByteArrayInputStream(m3u.toByteArray()) }

        val result = load()

        assertTrue(result is PlaylistLoadResult.Success)
        assertEquals(m3u, (result as PlaylistLoadResult.Success).text)
    }

    /**
     * The regression this test exists for.
     *
     * `ACTION_OPEN_DOCUMENT` grants access for the lifetime of the task, and a saved file playlist
     * outlives the task by design - its URI goes into the source list and is reloaded later. The
     * grant is gone by then, and `openInputStream` answers with `SecurityException`, which is not
     * an `IOException`: it used to leave this method uncaught, inside a `viewModelScope` coroutine,
     * which is a crash. Reproduced by switching to a file-backed playlist whose cached snapshot had
     * been cleared.
     */
    @Test
    fun aRevokedPermissionIsAnErrorAndNotACrash() = runTest {
        answerWith { throw SecurityException("Permission Denial: opening provider from uid 10123") }

        assertTrue("a revoked grant must not escape the loader", load() is PlaylistLoadResult.ReadError)
    }

    /** The file was deleted or moved after it was picked, or the storage holding it was unmounted.
     * SAF reports all three the same way. */
    @Test
    fun aDeletedOrMovedFileIsAnError() = runTest {
        answerWith { throw FileNotFoundException("No such file or directory") }

        assertTrue(load() is PlaylistLoadResult.ReadError)
    }

    /**
     * A provider is somebody else's code, and it is under no obligation to fail in a way this app
     * anticipated. A source list can outlive the app that issued its URIs; the authority is then
     * rejected rather than opened, and what comes back is whatever that resolver felt like
     * throwing - none of it an [IOException].
     *
     * Each of these used to end the process. They are asserted as a set rather than one by one
     * because the lesson is the set: the loader may not be a list of the failures known so far.
     */
    @Test
    fun noExceptionAProviderCanThrowReachesTheCaller() = runTest {
        val fromForeignCode = listOf(
            SecurityException("Permission Denial"),
            IllegalArgumentException("Unknown URL content://com.uninstalled.provider/doc/1"),
            IllegalStateException("provider closed"),
            UnsupportedOperationException("not implemented"),
            NullPointerException(),
        )

        for (thrown in fromForeignCode) {
            answerWith { throw thrown }

            assertTrue(thrown.javaClass.simpleName, load() is PlaylistLoadResult.ReadError)
        }
    }

    /** Storage that failed mid-read: a dying SD card, a network provider that dropped. */
    @Test
    fun aReadThatFailsPartWayIsAnError() = runTest {
        answerWith {
            object : InputStream() {
                private var served = 0
                override fun read(): Int = if (served++ < 10) '#'.code else throw IOException("I/O error")
            }
        }

        assertTrue(load() is PlaylistLoadResult.ReadError)
    }

    /**
     * A zero-byte file is not an error - it is a file with nothing in it, and the parser above
     * finds no channels. Worth pinning: turning this into a ReadError would be a lie about what
     * happened, and crashing on it would be worse.
     */
    @Test
    fun aZeroByteFileReadsAsEmptyText() = runTest {
        answerWith { ByteArrayInputStream(ByteArray(0)) }

        val result = load()

        assertTrue(result is PlaylistLoadResult.Success)
        assertEquals("", (result as PlaylistLoadResult.Success).text)
    }

    /** Binary rubbish - a JPEG, a database, a file the user picked by mistake. It decodes to
     * something, finds no channels, and must not throw on the way. */
    @Test
    fun aFileFullOfBinaryRubbishDoesNotThrow() = runTest {
        answerWith { ByteArrayInputStream(ByteArray(4096) { (it * 31 % 256).toByte() }) }

        assertTrue(load() is PlaylistLoadResult.Success)
    }

    /** The cap exists so a wrong pick - a video, a disk image - cannot be pulled into memory. It
     * has to be reported as its own outcome, not as a read failure and not as an empty playlist. */
    @Test
    fun aFileOverTheSizeCapIsRefusedRatherThanRead() = runTest {
        answerWith { ByteArrayInputStream(ByteArray(PlaylistUrlLoader.MAX_PLAYLIST_BYTES + 1)) }

        assertEquals(PlaylistLoadResult.SizeLimitExceeded, load())
    }

    /** Called on every load of a file playlist, including ones whose provider will refuse. Refusal
     * is the ordinary case for a URI that did not come from ACTION_OPEN_DOCUMENT, and it must leave
     * the app exactly where it was. */
    @Test
    fun rememberingAccessNeverThrows() {
        PlaylistFileLoader(context).rememberAccess(uri)
        PlaylistFileLoader(context).rememberAccess(Uri.parse("file:///sdcard/nope.m3u"))
        PlaylistFileLoader(context).rememberAccess(Uri.EMPTY)
    }
}
