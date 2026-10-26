package com.uacastplayer.data.backup

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.backup.BackupCodec
import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupMergePolicy
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistFileLoader
import com.uacastplayer.playlist.M3uParser
import com.uacastplayer.playlist.PlaylistLoadResult
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupPlaylistFilesTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val original = Uri.parse("content://example.test/original.m3u")
    private val playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"Новини\",Україна España\nhttps://example.test/live\n"
    private fun data(source: BackupPlaylistSource = BackupPlaylistSource(
        "original-id", "FILE", original.toString(), "Мій плейлист", 123L,
    )) = BackupData(listOf(source), emptyList(), BackupSettings(bufferSize = "LARGE"))

    private fun portable(bytes: ByteArray = playlist.toByteArray()): BackupData {
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) { ByteArrayInputStream(bytes) }
        return requireNotNull(BackupPlaylistFiles(application).capture(data(), Job()))
    }

    private fun merge(prepared: BackupData, existing: List<PlaylistSource> = emptyList()) =
        BackupMergePolicy.merge(existing, emptyList(), prepared.sources, emptyList())

    @Test fun `restore survives loss of original provider and loads through normal file pipeline`() = runTest {
        val store = BackupPlaylistFiles(application)
        val archived = BackupCodec.decode(BackupCodec.encode(portable()))!!
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) { throw IOException("removed") }
        val prepared = store.prepare(archived)!!
        val merged = merge(prepared)
        assertTrue(store.materialize(prepared, merged.sources, Job()))
        val restored = merged.sources.single()
        assertNotEquals(original.toString(), restored.location)
        assertEquals(Fingerprint.of(restored.location), restored.id)
        val loaded = PlaylistFileLoader(application).load(Uri.parse(restored.location)) as PlaylistLoadResult.Success
        assertEquals("Україна España", M3uParser.parse(loaded.text).channels.single().displayName)
        assertArrayEquals(playlist.toByteArray(), File(Uri.parse(restored.location).path!!).readBytes())
        // A new store/controller, or a backup made again from the restored file, keeps its identity.
        val capturedAgain = BackupPlaylistFiles(application).capture(prepared, Job())!!
        assertEquals(prepared, store.prepare(capturedAgain))
        assertEquals(1, merge(prepared, merged.sources).sources.size)
    }

    @Test fun `Windows 1251 and UTF16 original bytes are preserved`() {
        val text = "#EXTM3U\n#EXTINF:-1,Україна\nhttps://example.test/live\n"
        for (charset in listOf(charset("windows-1251"), Charsets.UTF_16)) {
            val bytes = text.toByteArray(charset)
            val store = BackupPlaylistFiles(application)
            val prepared = store.prepare(portable(bytes))!!
            assertTrue(store.materialize(prepared, merge(prepared).sources, Job()))
            assertArrayEquals(bytes, File(Uri.parse(prepared.sources.single().location).path!!).readBytes())
        }
    }

    @Test fun `corrupted payload is rejected before files are written`() {
        val store = BackupPlaylistFiles(application)
        val backup = portable()
        val changed = backup.copy(sources = listOf(backup.sources.single().copy(playlistBase64 = "YmFk")))
        assertNull(store.prepare(changed))
        assertFalse(File(application.filesDir, "backup_playlists").exists())
    }

    @Test fun `invalid base64 with recomputed digest is also rejected`() {
        val encoded = "!!not-base64!!"
        val source = data().sources.single().copy(playlistBase64 = encoded, playlistDigest = Fingerprint.of(encoded))
        assertNull(BackupPlaylistFiles(application).prepare(data(source)))
    }

    @Test fun `no local playlist is silently omitted on export failure`() {
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) { throw IOException("no access") }
        assertNull(BackupPlaylistFiles(application).capture(data(), Job()))
    }

    @Test fun `empty local file cannot claim a portable backup`() {
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) {
            ByteArrayInputStream(byteArrayOf())
        }
        assertNull(BackupPlaylistFiles(application).capture(data(), Job()))
    }

    @Test fun `payload aggregate is bounded before json encoding`() {
        val bytes = ByteArray(BackupCodec.MAX_BACKUP_BYTES / 2) { 'a'.code.toByte() }
        shadowOf(application.contentResolver).registerInputStreamSupplier(original) { ByteArrayInputStream(bytes) }
        val backup = data().let { it.copy(sources = it.sources + it.sources) }
        assertNull(BackupPlaylistFiles(application).capture(backup, Job()))
    }

    @Test fun `cancellation is never converted into a successful or failed file`() {
        val job = Job().apply { cancel() }
        assertThrows(CancellationException::class.java) { BackupPlaylistFiles(application).capture(data(), job) }
    }

    @Test fun `untrusted paths and display names cannot choose the destination`() {
        val malicious = portable().let { it.copy(sources = listOf(it.sources.single().copy(
            id = "../../escape", location = "file:///data/private", displayName = "../../escape.m3u",
        ))) }
        val store = BackupPlaylistFiles(application)
        val prepared = store.prepare(malicious)!!
        val file = File(Uri.parse(prepared.sources.single().location).path!!).canonicalFile
        assertEquals(File(application.filesDir, "backup_playlists").canonicalFile, file.parentFile)
        assertTrue(file.name.matches(Regex("[a-f0-9]{64}\\.m3u")))
        assertTrue(store.materialize(prepared, merge(prepared).sources, Job()))
    }

    @Test fun `sources over the saved source limit do not create orphan local files`() {
        val store = BackupPlaylistFiles(application)
        val prepared = store.prepare(portable())!!
        val existing = (1..10).map {
            PlaylistSource("$it", PlaylistSourceType.URL, "https://example.test/$it", null, 0)
        }
        val merged = merge(prepared, existing)
        assertEquals(1, merged.sourceLimitExceededCount)
        assertTrue(store.materialize(prepared, merged.sources, Job()))
        assertFalse(File(application.filesDir, "backup_playlists").exists())
    }

}
