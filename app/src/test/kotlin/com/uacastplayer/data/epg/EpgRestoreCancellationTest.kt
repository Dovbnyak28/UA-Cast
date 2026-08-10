package com.uacastplayer.data.epg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cancelling a restore must cancel the caller, not quietly hand it back "there was no snapshot".
 *
 * [EpgRepository.restoreSnapshot] catches [Exception] so that a corrupt or truncated snapshot falls
 * back to a fresh download instead of taking startup down. `CancellationException` **is** an
 * `Exception`, and the work it wraps suspends - a v1 snapshot is upgraded through
 * `withContext(Dispatchers.Default)`, which on a real device against a real feed took 53 seconds
 * (see [com.uacastplayer.epg.EpgSnapshotCodec]). Anything that ends the scope during that window -
 * the user switching source, the ViewModel clearing - therefore arrived at the caller as a normal
 * `null` return, so the coroutine carried on running inside a cancelled scope and the log blamed a
 * healthy snapshot for the failure.
 *
 * The rule the rest of the app already follows is in `DlnaSessionRepository.discoverDevices`:
 * cancellation is not a failure, and is rethrown ahead of the generic catch.
 *
 * **On the timing here.** The parse is ordinary blocking CPU work with no suspension point in it,
 * so there is no way to hold it open from the outside; the test instead waits until [XmlTvParser] is
 * genuinely on a thread's stack before cancelling, rather than sleeping for a guessed interval. That
 * is what makes it a test of *this* window and not of whatever the timer happened to hit: if the
 * cancel landed early - during the snapshot's file read, say - it would propagate correctly even
 * unfixed and the test would pass while proving nothing. The one residual is the opposite failure:
 * a machine loaded heavily enough that the whole 40k-programme parse finishes inside the few
 * milliseconds between detection and `cancel()` would fail this test rather than pass it, which is
 * the direction a timing assumption should break in.
 */
@RunWith(RobolectricTestRunner::class)
class EpgRestoreCancellationTest {

    private companion object {
        /** Big enough that the parse lasts far longer than the poll interval below, small enough
         * that writing it costs a fraction of a second. Well under the parser's 250k cap. */
        const val CHANNELS = 100
        const val PROGRAMMES_PER_CHANNEL = 400
        const val POLL_INTERVAL_MILLIS = 2L
        const val PARSE_START_TIMEOUT_MILLIS = 10_000L
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * A v1 snapshot - the format that stored the raw XMLTV document - written by hand, because that
     * is the one restore path that has real work to cancel. The layout is
     * [com.uacastplayer.epg.EpgSnapshotCodec]'s: version, fingerprint, timestamp, an unused length,
     * then the document itself, plain rather than gzipped (the repository sniffs for the magic
     * number and finds none).
     */
    private fun writeDocumentSnapshot() {
        val file = File(context.filesDir, "epg_snapshot.bin")
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(1)
            out.writeUTF("fingerprint")
            out.writeLong(System.currentTimeMillis())
            out.writeLong(0L)
            out.writeBytes("<tv>")
            for (channel in 0 until CHANNELS) {
                out.writeBytes(
                    "<channel id=\"ch$channel\"><display-name>Channel $channel</display-name></channel>",
                )
            }
            for (channel in 0 until CHANNELS) {
                for (slot in 0 until PROGRAMMES_PER_CHANNEL) {
                    out.writeBytes(
                        "<programme start=\"20240101000000 +0000\" stop=\"20240101003000 +0000\" " +
                            "channel=\"ch$channel\"><title>Programme $slot on ch$channel</title></programme>",
                    )
                }
            }
            out.writeBytes("</tv>")
        }
    }

    /** True once the parse is actually running - see the class doc for why this is a stack check
     * and not a sleep. */
    private fun parseIsInFlight(): Boolean = Thread.getAllStackTraces().values.any { frames ->
        frames.any { frame -> frame.className.contains("XmlTvParser") }
    }

    private suspend fun awaitParseInFlight(): Boolean {
        val deadline = System.currentTimeMillis() + PARSE_START_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (parseIsInFlight()) return true
            delay(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    @Test
    fun `cancelling mid-restore cancels the caller rather than returning no snapshot`() = runBlocking {
        writeDocumentSnapshot()
        val repository = EpgRepository(context)
        val returnedNormally = AtomicBoolean(false)

        val job = CoroutineScope(Dispatchers.Default).launch {
            repository.restoreSnapshot()
            // Reached only if the cancellation was swallowed: a propagated one ends the coroutine
            // at the suspension point it was thrown from.
            returnedNormally.set(true)
        }
        assertTrue("the parse never started, so this test cancelled nothing", awaitParseInFlight())
        job.cancelAndJoin()

        assertFalse(
            "restoreSnapshot swallowed the cancellation and reported an absent snapshot instead",
            returnedNormally.get(),
        )
    }
}
