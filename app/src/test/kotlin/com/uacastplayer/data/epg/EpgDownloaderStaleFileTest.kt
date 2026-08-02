package com.uacastplayer.data.epg

import java.io.File
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Every path through [EpgDownloader] and [EpgRepository] deletes its own temp file - but none of
 * that runs when the process itself is killed mid-download or mid-parse, which is exactly where this
 * app has been killed before. Nothing swept the leftovers, and they live in `filesDir` where Android
 * never reclaims them: a real device turned up 13 orphans totalling ~500MB.
 */
class EpgDownloaderStaleFileTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun downloader(dir: File) = EpgDownloader(OkHttpClient(), dir)

    private fun fileAged(dir: File, name: String, ageMillis: Long): File =
        File(dir, name).apply {
            writeText("x")
            check(setLastModified(System.currentTimeMillis() - ageMillis)) { "could not age $name" }
        }

    @Test
    fun `an orphaned download from a previous run is deleted`() {
        val dir = tempFolder.newFolder()
        val orphan = fileAged(dir, "epg_download_123.tmp", ageMillis = 2 * ONE_HOUR)

        downloader(dir).deleteStaleDownloads()

        assertFalse("stale orphan should be gone", orphan.exists())
    }

    /** The sweep runs immediately before a download starts, so it must not be able to delete a file
     * a concurrent download is still writing into - which is what the age gate is for. */
    @Test
    fun `a download still in progress is left alone`() {
        val dir = tempFolder.newFolder()
        val inFlight = fileAged(dir, "epg_download_456.tmp", ageMillis = 0)

        downloader(dir).deleteStaleDownloads()

        assertTrue("an in-flight download must survive the sweep", inFlight.exists())
    }

    @Test
    fun `unrelated files in the same directory are never touched`() {
        val dir = tempFolder.newFolder()
        val snapshot = fileAged(dir, "epg_snapshot.bin", ageMillis = 2 * ONE_HOUR)
        val playlist = fileAged(dir, "playlist_snapshot_abc.bin", ageMillis = 2 * ONE_HOUR)
        val favorites = fileAged(dir, "favorites.json", ageMillis = 2 * ONE_HOUR)

        downloader(dir).deleteStaleDownloads()

        assertTrue(snapshot.exists())
        assertTrue(playlist.exists())
        assertTrue(favorites.exists())
    }

    /** The reported state, reproduced: several complete feeds stranded by repeated process deaths. */
    @Test
    fun `many stranded downloads are all reclaimed`() {
        val dir = tempFolder.newFolder()
        val orphans = (1..13).map { fileAged(dir, "epg_download_$it.tmp", ageMillis = 2 * ONE_HOUR) }

        downloader(dir).deleteStaleDownloads()

        assertTrue("all 13 should be gone", orphans.none { it.exists() })
    }

    @Test
    fun `an empty or missing directory is not an error`() {
        downloader(tempFolder.newFolder()).deleteStaleDownloads()
        downloader(File(tempFolder.root, "does-not-exist")).deleteStaleDownloads()
    }

    private companion object {
        const val ONE_HOUR = 60L * 60L * 1000L
    }
}
