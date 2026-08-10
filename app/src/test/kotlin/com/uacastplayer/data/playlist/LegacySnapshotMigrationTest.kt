package com.uacastplayer.data.playlist

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one-time upgrade off the pre-multi-playlist snapshot file, and the crash window it used to
 * have.
 *
 * Migrations are the code least likely to be exercised and most expensive to get wrong: it runs on
 * exactly one launch per install, on a device the author does not have, while the app is doing more
 * work than at any other moment - which is also when Android is most likely to kill it. The failure
 * is not a crash but a silence: the playlist is simply gone the next time the app opens.
 */
@RunWith(RobolectricTestRunner::class)
class LegacySnapshotMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The fixed name the single-playlist build wrote, and the only thing that build left behind. */
    private val legacyFile: File get() = File(context.filesDir, "playlist_snapshot.bin")

    private val snapshot = PlaylistSnapshot(
        sourceFingerprint = "fp-legacy",
        savedAtEpochMillis = 1_700_000_000_000L,
        channels = listOf(
            M3uChannel(displayName = "Перший", streamUrl = "https://example/1.m3u8"),
            M3uChannel(displayName = "Другий", streamUrl = "https://example/2.m3u8"),
        ),
        skippedLineCount = 3,
        sourceUrl = "https://example/playlist.m3u",
    )

    @Before
    fun writeLegacySnapshot() {
        context.filesDir.mkdirs()
        legacyFile.outputStream().use { PlaylistSnapshotCodec.encode(snapshot, it) }
    }

    @Test
    fun theLegacyPlaylistBecomesTheFirstSavedSource() = runTest {
        val migrated = PlaylistRepository(context).migrateLegacySnapshotIfNeeded()

        assertNotNull("a legacy snapshot on disk must produce a source", migrated)
        assertEquals("fp-legacy", migrated!!.id)
        assertEquals("https://example/playlist.m3u", migrated.location)
        assertEquals(snapshot.savedAtEpochMillis, migrated.addedAtEpochMillis)

        val moved = PlaylistSnapshotStore(context, migrated.id).load()
        assertEquals("the channels have to survive the move", 2, moved?.channels?.size)
        assertEquals("Перший", moved?.channels?.first()?.displayName)
    }

    /**
     * The regression this test exists for.
     *
     * The migration used to delete the legacy file itself, before the caller had written the source
     * list that names the copy. A process death in between - a low-memory kill on the first launch
     * after an update - left the sources list empty and the legacy file gone, so the next launch
     * found nothing to migrate. The playlist was lost while its bytes sat in a per-source file
     * nothing referenced.
     */
    @Test
    fun theLegacySnapshotSurvivesTheMigrationItself() = runTest {
        PlaylistRepository(context).migrateLegacySnapshotIfNeeded()

        assertTrue(
            "the legacy file is the only record of the playlist until the source list is written",
            legacyFile.isFile,
        )
    }

    /** And therefore: a migration interrupted before the commit is simply run again, and recovers. */
    @Test
    fun anInterruptedMigrationRecoversOnTheNextLaunch() = runTest {
        val interrupted = PlaylistRepository(context)
        interrupted.migrateLegacySnapshotIfNeeded()
        // The process dies here - saveSources never ran, so loadSources() is still empty.

        val onNextLaunch = PlaylistRepository(context)
        assertTrue("nothing was committed, so there is nothing to load", onNextLaunch.loadSources().isEmpty())

        val recovered = onNextLaunch.migrateLegacySnapshotIfNeeded()
        assertNotNull("the second attempt must find the playlist again", recovered)
        assertEquals(2, PlaylistSnapshotStore(context, recovered!!.id).load()?.channels?.size)
    }

    @Test
    fun discardingRetiresTheFileSoTheMigrationNeverRunsAgain() = runTest {
        val repository = PlaylistRepository(context)
        repository.migrateLegacySnapshotIfNeeded()
        repository.discardLegacySnapshot()

        assertFalse(legacyFile.exists())
        assertNull("a completed migration must not re-adopt anything", repository.migrateLegacySnapshotIfNeeded())
    }

    /** Called on every launch that finds no sources, including every fresh install - so it has to
     * be uneventful when there is nothing there. */
    @Test
    fun afreshInstallHasNothingToMigrateAndDiscardIsHarmless() = runTest {
        legacyFile.delete()
        val repository = PlaylistRepository(context)

        assertNull(repository.migrateLegacySnapshotIfNeeded())
        repository.discardLegacySnapshot()
        assertNull(repository.migrateLegacySnapshotIfNeeded())
    }
}
