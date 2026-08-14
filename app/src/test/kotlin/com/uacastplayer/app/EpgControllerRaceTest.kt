package com.uacastplayer.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.epg.EpgRepository
import com.uacastplayer.data.prefs.AppPreferences
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which guide wins when the user changes their mind while one is still downloading.
 *
 * [EpgController] launched every load into the scope with nothing tracking it, so two loads could
 * be in flight at once and the *last to finish* won - not the last the user picked. An EPG feed is
 * tens of megabytes and takes far longer than the tap that abandons it, so the losing source is
 * routinely the slower one, and the window is the whole download.
 *
 * It did not stop at the screen either. A load also writes the cache, and there is exactly one
 * `epg_snapshot.bin` (see [com.uacastplayer.data.epg.EpgSnapshotStore]), so the abandoned source
 * overwrote the chosen one's snapshot too: Settings named one source, the guide belonged to
 * another, and a restart restored the wrong one rather than correcting it.
 *
 * There is no MockWebServer in this project (see `CastRoutingIntegrationTest`), so the servers here
 * are sockets, one of which holds its answer back until the test releases it - that hold is the
 * slow feed. As in [PlaylistControllerRemovalTest] the two tests are a pair: the second serves the
 * same held feed with nothing competing and asserts it *does* land, which is what gives the first
 * test's "it never lands" a measured window rather than a hopeful one.
 */
@RunWith(RobolectricTestRunner::class)
class EpgControllerRaceTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Serves one XMLTV document naming a single channel [channelId]; when [hold] is set it does
     * not answer until [release] is called, which is what makes a load "still in flight". */
    private class XmlTvServer(channelId: String, private val hold: Boolean = false) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()
        private val released = CountDownLatch(1)

        /** Counts down once the client's request has been read in full. */
        val requestReceived = CountDownLatch(1)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/guide.xml"

        private val document = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="$channelId">
                <display-name>$channelId</display-name>
              </channel>
            </tv>
        """.trimIndent()

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
                        if (hold) released.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        val payload = document.toByteArray()
                        val head = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/xml\r\n" +
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

    private val applied = AtomicInteger()

    private fun controller() = EpgController(
        preferences = AppPreferences(application),
        epgRepository = EpgRepository(application),
        scope = scope,
        onLoaded = { applied.incrementAndGet() },
    )

    /** The channel ids of whatever guide is currently on screen, or null when none has loaded. */
    private fun EpgController.channelIds(): List<String>? =
        epgState.value.data?.index?.channels?.map { it.id }

    private fun EpgController.awaitChannels(expected: List<String>, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && channelIds() != expected) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return channelIds() == expected
    }

    /**
     * The bug: a guide the user navigated away from arrives late and takes the screen back.
     *
     * Without the cancel in `launchLoad`, the held feed's channel replaces the chosen one within
     * milliseconds of the server being released.
     */
    @Test
    fun `a guide the user moved on from does not replace the one they chose`() {
        XmlTvServer("abandoned", hold = true).use { slow ->
            XmlTvServer("chosen").use { fast ->
                val controller = controller()

                controller.applyCustomEpgUrl(slow.url, markChosen = true)
                assertTrue(
                    "the first guide should have reached its server",
                    slow.requestReceived.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )

                controller.applyCustomEpgUrl(fast.url, markChosen = true)
                assertTrue(
                    "the second choice should be the one that loads",
                    controller.awaitChannels(listOf("chosen"), LOAD_WAIT_MILLIS),
                )

                slow.release()

                // Everything the abandoned feed would do happens well inside this window - the
                // second test measures exactly that path with nothing competing.
                Thread.sleep(ABSENCE_WAIT_MILLIS)
                assertEquals(
                    "the abandoned source must not take the guide back",
                    listOf("chosen"),
                    controller.channelIds(),
                )
                assertEquals(
                    "the chosen source's url must stay the selected one",
                    fast.url,
                    controller.epgState.value.customUrl,
                )
            }
        }
    }

    /**
     * The control. Same held server, same waits, nothing competing - so the load does land, which
     * is what makes the absence asserted above mean "cancelled" rather than "not finished yet".
     */
    @Test
    fun `a guide with nothing competing for it is applied`() {
        XmlTvServer("only", hold = true).use { slow ->
            val controller = controller()

            controller.applyCustomEpgUrl(slow.url, markChosen = true)
            assertTrue(
                "the guide should have reached its server",
                slow.requestReceived.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            slow.release()

            assertTrue(
                "an uncontested load must reach the screen",
                controller.awaitChannels(listOf("only"), ABSENCE_WAIT_MILLIS),
            )
        }
    }

    private companion object {
        const val HOLD_TIMEOUT_SECONDS = 10L
        const val LOAD_WAIT_MILLIS = 10_000L

        /**
         * How long the first test waits for something that must never happen, and the ceiling the
         * second test proves is far more than the same work needs.
         */
        const val ABSENCE_WAIT_MILLIS = 3_000L
        const val POLL_INTERVAL_MILLIS = 20L
    }
}
