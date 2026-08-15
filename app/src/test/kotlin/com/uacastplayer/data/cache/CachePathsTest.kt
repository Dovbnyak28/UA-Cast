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

    /**
     * This test used to say `AtomicFile` keeps a `.bak` beside every file it writes, and it passed
     * because it created that `.bak` itself. It does not: measured against the real implementation,
     * a write in progress produces `<name>.new` and nothing else. So the suffix being matched could
     * not exist and the one that does was invisible - see [AtomicSnapshotDeletionTest], which
     * abandons a real write rather than naming the file by hand.
     *
     * `.bak` is still counted, because a file with that name is stale cache whatever left it there.
     */
    @Test
    fun whatAnUnfinishedWriteLeavesBehindCountsAsCacheToo() {
        write("playlist_snapshot_abc123.bin", 10)
        write("playlist_snapshot_abc123.bin.new", 10)
        write("playlist_snapshot_abc123.bin.bak", 5)

        assertEquals(25L, CacheSizeUtils.sizeOf(CachePaths.playlistSnapshots(folder.root)))
    }

    /**
     * The one that is stranded for good. A removed source's id is never written again, so nothing
     * will rename or truncate its leftover, and `filesDir` is not a directory Android reclaims -
     * this listing is the only mechanism left that can reach it. Measured at 5.6MB for a
     * 40,000-channel playlist.
     */
    @Test
    fun anUnfinishedWriteWithNoBaseFileIsStillFound() {
        write("playlist_snapshot_removed.bin.new", 10)

        assertEquals(
            listOf("playlist_snapshot_removed.bin.new"),
            names(CachePaths.playlistSnapshots(folder.root)),
        )
    }

    /** The EPG snapshot has the same leftover, and an EPG parse is where this app has been killed
     * before - so the row that reports it has to look at both names. */
    @Test
    fun theEpgSnapshotAndItsUnfinishedWriteAreBothAccountedFor() {
        write(CachePaths.EPG_SNAPSHOT, 30)
        write(CachePaths.EPG_SNAPSHOT + CachePaths.ATOMIC_WRITE_SUFFIX, 12)

        assertEquals(42L, CacheSizeUtils.sizeOf(CachePaths.epgSnapshots(folder.root)))

        CacheSizeUtils.clear(CachePaths.epgSnapshots(folder.root))

        assertEquals(0L, CacheSizeUtils.sizeOf(CachePaths.epgSnapshots(folder.root)))
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
        write("playlist_snapshot_def.bin.new", 10)
        write("playlist_snapshot_ghi.bin.bak", 10)
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
