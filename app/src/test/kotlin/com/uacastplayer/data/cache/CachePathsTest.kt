package com.uacastplayer.data.cache

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Which files the cache screen is actually looking at.
 *
 * This is the failure mode a cache screen has: it does not crash, it reports 0 B. The screen used
 * to name the single pre-multi-playlist snapshot file, so on every install created since - which is
 * all of them - the playlist row read as empty and its Clear button deleted a file that was not
 * there, while the real snapshots sat beside it, reachable from no UI at all.
 */
class CachePathsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun write(name: String, bytes: Int) {
        File(folder.root, name).writeBytes(ByteArray(bytes))
    }

    private fun names(files: List<File>) = files.map { it.name }.sorted()

    @Test
    fun everyPerSourceSnapshotIsFound() {
        write("playlist_snapshot_abc123.bin", 10)
        write("playlist_snapshot_def456.bin", 20)

        assertEquals(
            listOf("playlist_snapshot_abc123.bin", "playlist_snapshot_def456.bin"),
            names(CachePaths.playlistSnapshots(folder.root)),
        )
    }

    /** `AtomicFile` keeps a `.bak` beside every file it writes. It is the same cache, it is the
     * same bytes on disk, and a size that ignored it would be wrong by up to a factor of two. */
    @Test
    fun atomicFileBackupsCountAsCacheToo() {
        write("playlist_snapshot_abc123.bin", 10)
        write("playlist_snapshot_abc123.bin.bak", 10)

        assertEquals(20L, CacheSizeUtils.sizeOf(CachePaths.playlistSnapshots(folder.root)))
    }

    /** An install that upgraded but has not yet had its first launch still has the old file, and
     * it is real bytes the user should be able to see and remove. */
    @Test
    fun theLegacySnapshotIsStillAccountedFor() {
        write(CachePaths.LEGACY_PLAYLIST_SNAPSHOT, 30)

        assertEquals(listOf(CachePaths.LEGACY_PLAYLIST_SNAPSHOT), names(CachePaths.playlistSnapshots(folder.root)))
    }

    /** The one thing this must never do is widen into "everything in filesDir" - that directory
     * holds the license, the favourites and the parental-control lock list, none of which is cache
     * and all of which sit one careless prefix away. */
    @Test
    fun nothingElseInFilesDirIsTreatedAsPlaylistCache() {
        write("playlist_snapshot_abc.bin", 10)
        write("epg_snapshot.bin", 10)
        write("favorites.json", 10)
        write("parental_control_locked_channels.json", 10)
        write("playlist_sources.bin", 10)
        File(folder.root, "icon_cache").mkdirs()

        assertEquals(listOf("playlist_snapshot_abc.bin"), names(CachePaths.playlistSnapshots(folder.root)))
    }

    @Test
    fun aFreshInstallReportsNothingRatherThanFailing() {
        assertTrue(CachePaths.playlistSnapshots(folder.root).isEmpty())
        assertEquals(0L, CacheSizeUtils.sizeOf(CachePaths.playlistSnapshots(folder.root)))
    }

    /** Clearing has to remove all of them, not the first - two saved playlists are the ordinary
     * case for anyone the multi-playlist feature was built for. */
    @Test
    fun clearingRemovesEverySnapshot() {
        write("playlist_snapshot_abc.bin", 10)
        write("playlist_snapshot_def.bin", 10)
        write("playlist_snapshot_def.bin.bak", 10)
        write("favorites.json", 10)

        CacheSizeUtils.clear(CachePaths.playlistSnapshots(folder.root))

        assertTrue(CachePaths.playlistSnapshots(folder.root).isEmpty())
        assertTrue("clearing a cache must not touch saved data", File(folder.root, "favorites.json").isFile)
    }

    /** A file that vanished between listing and measuring - a prefetch trimming beside us, or the
     * user clearing from the system settings screen - is zero bytes, not a crash. */
    @Test
    fun aFileThatDisappearsMeasuresAsZero() {
        val gone = File(folder.root, "playlist_snapshot_gone.bin")

        assertEquals(0L, CacheSizeUtils.sizeOf(gone))
        assertEquals(0L, CacheSizeUtils.sizeOf(listOf(gone)))
    }
}
