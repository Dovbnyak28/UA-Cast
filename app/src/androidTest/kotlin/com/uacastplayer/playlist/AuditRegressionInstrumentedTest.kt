package com.uacastplayer.playlist

import android.content.ContextWrapper
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.data.playlist.PlaylistSnapshotStore
import com.uacastplayer.data.cast.ProxyManifestResources
import com.uacastplayer.data.cast.ResourceEntry
import com.uacastplayer.data.cast.RESOURCE_TYPE_PLAYLIST
import com.uacastplayer.epg.XmlTvParser
import com.uacastplayer.proxy.HlsMediaPlaylistParser
import com.uacastplayer.proxy.PlaylistUnwrapPolicy
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic regression inputs only; never reads or changes the installed release app's data. */
@RunWith(AndroidJUnit4::class)
class AuditRegressionInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun hostileHlsIsRejectedBeforeUnwrapAllocationOnArt() {
        val input = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" + "a\n".repeat(2_000_000)
        measure("hls-unwrap-structural-budget") {
            assertThrows(IOException::class.java) {
                PlaylistUnwrapPolicy.unwrapTarget(input, "https://example.test/root.m3u8")
            }
            assertThrows(IOException::class.java) { HlsMediaPlaylistParser.parse(input) }
            val healthy = HlsMediaPlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\na.ts\n")
            assertEquals(listOf("a.ts"), healthy.segmentUris)
        }
    }

    @Test fun repeatedHlsReferencesCannotAmplifyMemoryOnArt() {
        val input = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" + "a\n".repeat(1_000_000)
        val parent = ResourceEntry(RESOURCE_TYPE_PLAYLIST, "https://example.test/root.m3u8", "Audit", null)
        val leases = ProxyManifestResources().apply { beginRoot(parent) }
        measure("hls-repeated-references") {
            assertNull(leases.rewrite(input, parent.originalUrl, parent) { error("Must reject before mapping") })
            assertEquals(1, leases.snapshot().size)
        }
    }

    @Test fun millionsOfOptionalEpgEntriesStayBoundedOnArt() {
        val input = "#EXTM3U url-tvg=\"" + "a,".repeat(3_500_000) +
            "\"\n#EXTINF:-1,News\nhttps://example.test/live"
        measure("m3u-epg-metadata") {
            val parsed = M3uParser.parse(input)
            assertEquals(1, parsed.channels.size)
            assertTrue(parsed.epgUrls.isEmpty())
        }
    }

    @Test fun repeatedXmlTvAliasesStayLinearOnArt() {
        val input = "<tv><channel id=\"one\">" +
            "<display-name>Name</display-name>".repeat(32_000) + "</channel></tv>"
        measure("xmltv-repeated-aliases") {
            val parsed = XmlTvParser.parse(input.byteInputStream())
            assertEquals(listOf("Name"), parsed.channels.single().displayNames)
            assertFalse(parsed.aliasLimitExceeded)
        }
    }

    @Test fun snapshotReportsBothOpenAndCommitFailuresOnAndroid() = runBlocking {
        val directory = File(context.cacheDir, "snapshot-fault-${System.nanoTime()}")
        check(directory.mkdir())
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }
        val snapshot = PlaylistSnapshot(
            "audit", 1L, listOf(M3uChannel("News", "https://example.test/live")), 0, null,
        )
        try {
            val store = PlaylistSnapshotStore(isolated, snapshot.sourceFingerprint)
            for (suffix in listOf(".bin.new", ".bin")) {
                val blocked = File(directory, "playlist_snapshot_audit$suffix")
                check(blocked.mkdir())
                val marker = File(blocked, "owned-test-marker").apply { writeText("keep") }
                assertFalse("Failed $suffix write must not retire legacy data", store.save(snapshot))
                assertTrue(marker.isFile)
                check(marker.delete())
                check(blocked.delete())
            }
            assertTrue(store.save(snapshot))
            assertTrue(store.save(snapshot.copy(savedAtEpochMillis = 2L)))
            assertEquals(2L, store.load()?.savedAtEpochMillis)
            assertEquals("News", store.load()?.channels?.single()?.displayName)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun measure(name: String, action: () -> Unit) {
        val before = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val started = System.nanoTime()
        action()
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val after = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val allocated = if (before != null && after != null) after - before else null
        val directory = checkNotNull(context.getExternalFilesDir("player-controls-audit"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.txt").writeText(
            "Synthetic debug ART regression; excludes input construction; not peak heap\n" +
                "elapsedMs=$elapsed processAllocatedBytes=$allocated maxHeapBytes=${Runtime.getRuntime().maxMemory()}\n",
        )
        // Loose allocation guard catches the old hundreds-of-MB / multi-GB amplification.
        if (allocated != null) assertTrue("$name allocated $allocated bytes", allocated < 96L * 1024 * 1024)
    }
}
