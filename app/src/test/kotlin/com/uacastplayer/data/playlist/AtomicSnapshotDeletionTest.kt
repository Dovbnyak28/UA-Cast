package com.uacastplayer.data.playlist

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.core.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.cache.CachePaths
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSnapshot
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What is left on disk after a playlist source is removed.
 *
 * A snapshot is written through `AtomicFile`, which streams into `<name>.new` and renames that onto
 * the base name when the write finishes. A process killed in between leaves the `.new` file behind
 * - and `PlaylistRepository.deleteSnapshot` spelled the base name out a second time and handed it
 * to `File.delete()`, which does not touch it.
 *
 * For a *removed* source that leftover is stranded for good. Its id is never written again, so
 * nothing will rename or truncate it; no saved source names it, so nothing rebuilt from the source
 * list can find it; and `filesDir` is not a directory Android ever reclaims. `PlaylistController`'s
 * own comment already describes this outcome for the base file and calls it "orphaned... nothing
 * will ever delete it again" - the fix that put it there closed one door and left the one beside it
 * open.
 *
 * The leftovers here are made by abandoning a **real** `AtomicFile` write rather than by writing a
 * file called `.new` by hand. That distinction is the point: the previous test in this area passed
 * only because it invented the filename it was checking for, and invented the wrong one.
 */
@RunWith(RobolectricTestRunner::class)
class AtomicSnapshotDeletionTest {

    @get:Rule val temporaryFiles = TemporaryFolder()
    private lateinit var context: Context
    private val filesDir: File get() = context.filesDir

    private val snapshot = PlaylistSnapshot(
        sourceFingerprint = "f".repeat(64),
        savedAtEpochMillis = 1L,
        channels = listOf(M3uChannel(displayName = "One", streamUrl = "http://example.test/1.ts")),
        skippedLineCount = 0,
        sourceUrl = "http://example.test/list.m3u",
    )

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        context = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = temporaryFiles.root
        }
    }

    private fun snapshotFile(sourceId: String) = File(filesDir, "playlist_snapshot_$sourceId.bin")

    /** A write that started and never finished, which is what a process death mid-save looks like
     * on disk. Nothing here names the leftover - `AtomicFile` decides what it is called. */
    private fun abandonWriteFor(sourceId: String) {
        val stream = AtomicFile(snapshotFile(sourceId)).startWrite()
        stream.write(ByteArray(2048))
        stream.close()
    }

    private fun namesInFilesDir() = filesDir.listFiles()?.map { it.name }?.sorted().orEmpty()

    /** The environment this rests on, asserted rather than assumed: an unfinished write leaves one
     * extra file, and its name is the base name plus what [CachePaths.ATOMIC_WRITE_SUFFIX] says. */
    @Test
    fun `an unfinished write leaves exactly the file CachePaths expects`() {
        runBlocking { PlaylistSnapshotStore(context, "abc").save(snapshot) }

        abandonWriteFor("abc")

        assertEquals(
            listOf("playlist_snapshot_abc.bin", "playlist_snapshot_abc.bin" + CachePaths.ATOMIC_WRITE_SUFFIX),
            namesInFilesDir(),
        )
    }

    /** The bug. */
    @Test
    fun `removing a source leaves nothing of its snapshot behind`() {
        runBlocking { PlaylistSnapshotStore(context, "abc").save(snapshot) }
        abandonWriteFor("abc")

        runBlocking { PlaylistRepository(context).deleteSnapshot("abc") }

        assertEquals(emptyList<String>(), namesInFilesDir())
    }

    /**
     * The control, and it matters: a delete that removed nothing at all would also pass the test
     * above if the setup had never written anything. This one proves the base file was really there
     * and really went.
     */
    @Test
    fun `the snapshot itself is still what gets deleted`() {
        runBlocking { PlaylistSnapshotStore(context, "abc").save(snapshot) }
        assertTrue(snapshotFile("abc").isFile)

        runBlocking { PlaylistRepository(context).deleteSnapshot("abc") }

        assertFalse(snapshotFile("abc").exists())
    }

    /** Removing one source must not take another's snapshot - or its leftover - with it. */
    @Test
    fun `another source's files are left alone`() {
        runBlocking {
            PlaylistSnapshotStore(context, "abc").save(snapshot)
            PlaylistSnapshotStore(context, "def").save(snapshot)
        }
        abandonWriteFor("def")

        runBlocking { PlaylistRepository(context).deleteSnapshot("abc") }

        assertEquals(
            listOf("playlist_snapshot_def.bin", "playlist_snapshot_def.bin" + CachePaths.ATOMIC_WRITE_SUFFIX),
            namesInFilesDir(),
        )
    }

    /**
     * The same leftover on the one-time migration path, which every upgrading install runs. The
     * pre-multi-playlist snapshot was written through `AtomicFile` too, so a device killed mid-save
     * by the old build has one - and after this delete nothing in the app refers to either name
     * again.
     */
    @Test
    fun `retiring the legacy snapshot takes its unfinished write with it`() {
        val legacy = File(filesDir, CachePaths.LEGACY_PLAYLIST_SNAPSHOT)
        AtomicFile(legacy).startWrite().use { it.write(ByteArray(2048)) }
        legacy.writeBytes(ByteArray(512))
        assertEquals(2, namesInFilesDir().size)

        runBlocking { LegacyPlaylistSnapshotFile.delete(context) }

        assertEquals(emptyList<String>(), namesInFilesDir())
    }

    /** Deleting a source that was never saved is a no-op, not a failure - `removePlaylistSource`
     * calls this for every removal, including one whose load never got as far as a snapshot. */
    @Test
    fun `deleting a snapshot that is not there does nothing`() {
        runBlocking { PlaylistRepository(context).deleteSnapshot("never-saved") }

        assertEquals(emptyList<String>(), namesInFilesDir())
    }
    @Test fun `snapshot removal preserves unrelated diagnostics`() {
        val diagnostics = File(filesDir, "last_crash.txt").apply { writeText("unrelated diagnostics") }
        runBlocking { PlaylistSnapshotStore(context, "abc").save(snapshot) }
        abandonWriteFor("abc")
        runBlocking { PlaylistRepository(context).deleteSnapshot("abc") }
        assertEquals(listOf(diagnostics.name), namesInFilesDir())
        assertEquals("unrelated diagnostics", diagnostics.readText())
    }

}
