package com.uacastplayer.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What happens to a playlist load that is still in flight when the user deletes the playlist.
 *
 * [PlaylistController] was the last controller in this package with no test of its own, and the
 * defect this pins lived exactly there. A load writes a snapshot file when it finishes
 * (`PlaylistRepository.persistIfLoaded`), and `removePlaylistSource` deleted that file without
 * stopping the load that was about to write it again. Hitting refresh on a slow playlist and then
 * deleting it put the deleted playlist back on screen and left its snapshot file on disk - orphaned
 * this time, because no saved source names it any more, so nothing would ever delete it again.
 *
 * There is no MockWebServer in this project (see `CastRoutingIntegrationTest`), so the server here
 * is a socket that holds its answer back until the test releases it. That hold is what makes a load
 * "in flight" at a moment the test controls, which is the whole setup the bug needs.
 *
 * The two tests are a pair and neither stands alone. The first asserts an absence - that nothing is
 * written after the removal - and an absence is only worth as much as the wait behind it. The
 * second is the same machinery with the same waits on a source that is *not* removed, and it
 * asserts the snapshot does appear. So the second proves the pipeline finishes well inside the
 * window the first waits out, and it independently pins the other half of the fix: removing some
 * other source must leave the running load alone.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistControllerRemovalTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /**
     * Accepts one request, tells the test it has it, and answers only once released. Between those
     * two points the client is blocked in a socket read - which is precisely what "a load in
     * flight" means here.
     */
    private class HeldServer : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()
        private val released = CountDownLatch(1)

        /** Counts down once the client's request has been read in full. */
        val requestReceived = CountDownLatch(1)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/playlist.m3u"

        init {
            worker.submit {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        // Headers end at the first blank line, and a closed stream ends them too.
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            line = reader.readLine()
                        }
                        requestReceived.countDown()
                        released.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        val payload = PLAYLIST.toByteArray()
                        val head = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/vnd.apple.mpegurl\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray())
                            write(payload)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    // The socket being closed by close() below is how this thread ends.
                } catch (_: InterruptedException) {
                    // Same, for a shutdown that lands while the answer is still held back.
                }
            }
        }

        fun release() = released.countDown()

        override fun close() {
            released.countDown()
            worker.shutdownNow()
            socket.close()
        }
    }

    private class Loads {
        val loaded = AtomicInteger()
        val stateChanges = AtomicInteger()
    }

    private fun controllerFor(loads: Loads) = PlaylistController(
        preferences = AppPreferences(application),
        playlistRepository = PlaylistRepository(application),
        scope = scope,
        onLoaded = { _, _, _, _ -> loads.loaded.incrementAndGet() },
        onStateChanged = { loads.stateChanges.incrementAndGet() },
    )

    private fun sourceFor(url: String, addedAtEpochMillis: Long) = PlaylistSource(
        id = Fingerprint.of(url),
        type = PlaylistSourceType.URL,
        location = url,
        displayName = "Test",
        addedAtEpochMillis = addedAtEpochMillis,
    )

    private fun snapshotFileFor(url: String): File =
        File(application.filesDir, "playlist_snapshot_${Fingerprint.of(url)}.bin")

    /** Polls for [file] to appear, up to [timeoutMillis]. Returns whether it ever did. */
    private fun awaitFile(file: File, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && !file.exists()) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return file.exists()
    }

    /**
     * The bug. The active source is removed while its own load is still waiting on the network; the
     * answer arrives afterwards and must change nothing.
     *
     * Without the cancel in `removePlaylistSource`, the snapshot file appears within milliseconds of
     * the server being released, and `onLoaded` fires with the deleted playlist's channels.
     */
    @Test
    fun `a load still in flight for the removed source writes nothing back`() {
        HeldServer().use { server ->
            val loads = Loads()
            val controller = controllerFor(loads)
            val source = sourceFor(server.url, addedAtEpochMillis = 1L)
            controller.applyImportedSources(listOf(source))
            controller.setActivePlaylistSourceId(source.id)

            controller.refreshPlaylist()
            assertTrue(
                "the load should have reached the server",
                server.requestReceived.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            controller.removePlaylistSource(source.id)
            server.release()

            assertFalse(
                "the deleted playlist's snapshot must not be written back after its source is gone",
                awaitFile(snapshotFileFor(server.url), ABSENCE_WAIT_MILLIS),
            )
            assertEquals("no channels may be applied for a source that no longer exists", 0, loads.loaded.get())
            assertEquals(emptyList<PlaylistSource>(), controller.playlistSources.value)
            assertEquals(0, controller.channelCount)
        }
    }

    /**
     * The other half: the cancel is aimed at the source being removed, not at whatever load happens
     * to be running. Removing an *inactive* source leaves the active one's load alone, and this is
     * also the control that gives the absence above its meaning - same server, same waits, and here
     * the snapshot lands well inside them.
     */
    @Test
    fun `removing some other source leaves the running load alone`() {
        HeldServer().use { server ->
            val loads = Loads()
            val controller = controllerFor(loads)
            val active = sourceFor(server.url, addedAtEpochMillis = 2L)
            val other = sourceFor("http://127.0.0.1:1/other.m3u", addedAtEpochMillis = 1L)
            controller.applyImportedSources(listOf(active, other))
            controller.setActivePlaylistSourceId(active.id)

            controller.refreshPlaylist()
            assertTrue(
                "the load should have reached the server",
                server.requestReceived.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            controller.removePlaylistSource(other.id)
            server.release()

            assertTrue(
                "the surviving source's load must still be allowed to finish",
                awaitFile(snapshotFileFor(server.url), ABSENCE_WAIT_MILLIS),
            )
            assertEquals(listOf(active), controller.playlistSources.value)
        }
    }

    @Test
    fun `leaving add flow cancels unsaved source and leaves no orphan snapshot`() {
        HeldServer().use { server ->
            val loads = Loads()
            val controller = controllerFor(loads)

            controller.loadPlaylistFromUrl(server.url)
            assertTrue(
                "the pending source should have reached its server",
                server.requestReceived.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            controller.cancelPendingSourceAdd()
            server.release()

            assertFalse(
                "an abandoned add must not leave a snapshot no saved source can name",
                awaitFile(snapshotFileFor(server.url), ABSENCE_WAIT_MILLIS),
            )
            assertFalse(controller.playlistState.value.isLoading)
            assertEquals(0, loads.loaded.get())
            assertTrue(controller.playlistSources.value.isEmpty())
        }
    }

    private companion object {
        const val HOLD_TIMEOUT_SECONDS = 10L

        /**
         * How long the first test waits for something that must never happen. Generous on purpose:
         * the second test does the same work end to end and lands inside a small fraction of it, so
         * the window is not what decides either result.
         */
        const val ABSENCE_WAIT_MILLIS = 3_000L
        const val POLL_INTERVAL_MILLIS = 20L

        val PLAYLIST = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="Test",One
            http://example.test/one.ts
            #EXTINF:-1 tvg-id="two" group-title="Test",Two
            http://example.test/two.ts
        """.trimIndent()
    }
}
