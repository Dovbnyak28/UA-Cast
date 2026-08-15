package com.uacastplayer.data.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.FileDigest
import com.uacastplayer.update.ReleaseApk
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fetching a release's APK.
 *
 * This is the step between "GitHub says 0.9.1 exists" and a system install dialog, and it is the
 * one place a downloaded file's claims are checked against what the release said about it. There is
 * no MockWebServer in this project (see `CastRoutingIntegrationTest`), so the origin here is a
 * hand-rolled [ServerSocket] that serves a fixed body and counts how many requests reached it -
 * that count is what turns "it reused the cached file" from a guess into an assertion.
 *
 * What is deliberately *not* asserted here is whether the file may be installed. That is
 * [com.uacastplayer.update.ApkTrustPolicy]'s question, and answering it needs a real signed APK and
 * a real PackageManager. A download that arrives intact is still an untrusted file until that gate
 * runs.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateDownloaderTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    /** Serves [body] to every caller, or [status] when that is not 200. */
    private class Origin(private val body: ByteArray, private val status: Int = 200) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        val requests = AtomicInteger(0)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/uacast.apk"

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        requests.incrementAndGet()
                        worker.execute {
                            client.use {
                                // Read just the request line and headers; the body is irrelevant.
                                val input = it.getInputStream()
                                val buffer = ByteArray(2048)
                                input.read(buffer)
                                val out = it.getOutputStream()
                                val head = if (status == 200) {
                                    "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n" +
                                        "Content-Type: application/vnd.android.package-archive\r\n\r\n"
                                } else {
                                    "HTTP/1.1 $status Nope\r\nContent-Length: 0\r\n\r\n"
                                }
                                out.write(head.toByteArray())
                                if (status == 200) out.write(body)
                                out.flush()
                            }
                        }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    private val payload = ByteArray(40_000) { (it % 253).toByte() }
    private lateinit var payloadHash: String

    private val cacheDirectory: File get() = File(application.cacheDir, "updates")

    @Before
    fun setUp() {
        cacheDirectory.deleteRecursively()
        val scratch = File.createTempFile("payload", ".bin").apply { writeBytes(payload) }
        payloadHash = requireNotNull(FileDigest.sha256(scratch))
        scratch.delete()
    }

    @After
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    private fun apk(origin: Origin, size: Long = payload.size.toLong(), sha: String? = payloadHash) =
        ReleaseApk(downloadUrl = origin.url, sizeBytes = size, sha256 = sha)

    private fun download(apk: ReleaseApk, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        runBlocking { UpdateDownloader(application).download(apk, onProgress) }

    @Test
    fun `an apk that matches its published size and hash lands on disk intact`() {
        Origin(payload).use { origin ->
            val result = download(apk(origin))

            val ready = result as? UpdateDownload.Ready
            assertNotNull("expected Ready, got $result", ready)
            assertArrayEquals(payload, ready!!.file.readBytes())
            assertEquals(payloadHash, FileDigest.sha256(ready.file))
        }
    }

    /**
     * The check that exists because the hash is the only statement about *content* the release
     * makes. A body that arrives whole but is not the release's body has to be refused, and refused
     * without leaving an .apk behind for anything downstream to find.
     */
    @Test
    fun `a body that does not match the published hash is refused and nothing is published`() {
        Origin(ByteArray(40_000) { 7 }).use { origin ->
            val result = download(apk(origin))

            assertEquals(UpdateDownload.Corrupt, result)
            assertTrue("no .apk may survive a failed check", apksInCache().isEmpty())
        }
    }

    /**
     * A truncated download and a substituted one are different failures, and the size is what tells
     * them apart - a hash mismatch alone does not say why. It is also the only statement left when
     * a release published no hash at all.
     */
    @Test
    fun `a body of the wrong length is refused even when it hashes to itself`() {
        val short = payload.copyOf(20_000)
        Origin(short).use { origin ->
            val shortHash = File.createTempFile("short", ".bin").let {
                it.writeBytes(short)
                FileDigest.sha256(it).also { _ -> it.delete() }
            }

            val result = download(apk(origin, size = payload.size.toLong(), sha = shortHash))

            assertEquals(UpdateDownload.Corrupt, result)
        }
    }

    /**
     * Assets uploaded before GitHub recorded digests have none, which is an ordinary state rather
     * than a fault - see `ReleaseApkPolicy`. The download must still complete; the signature gate is
     * what stands between it and an install either way.
     */
    @Test
    fun `a release with no published hash still downloads`() {
        Origin(payload).use { origin ->
            val result = download(apk(origin, sha = null))

            assertTrue("expected Ready, got $result", result is UpdateDownload.Ready)
        }
    }

    @Test
    fun `an http error is a plain failure, not a corrupt file`() {
        Origin(payload, status = 404).use { origin ->
            assertEquals(UpdateDownload.Failed, download(apk(origin)))
            assertTrue(apksInCache().isEmpty())
        }
    }

    /**
     * The cap. Declared as a size the release does not honour, so this is the *streaming* bound
     * being exercised - a downloader that trusted Content-Length would sail past it.
     */
    @Test
    fun `a body past the cap is abandoned rather than written out`() {
        Origin(payload).use { origin ->
            // A cap smaller than the payload, since proving the real 300MB one would measure the
            // disk rather than the rule.
            val small = UpdateDownloader(application, maxBytes = 1024L)

            val result = runBlocking { small.download(apk(origin)) }

            assertEquals(UpdateDownload.TooLarge, result)
            assertTrue(apksInCache().isEmpty())
        }
    }

    /**
     * A finished download whose install the user dismissed must not be paid for twice. The request
     * count is what makes this an assertion rather than an assumption.
     */
    @Test
    fun `a verified apk already in the cache is reused without asking the origin again`() {
        Origin(payload).use { origin ->
            assertTrue(download(apk(origin)) is UpdateDownload.Ready)
            val afterFirst = origin.requests.get()

            val second = download(apk(origin))

            assertTrue(second is UpdateDownload.Ready)
            assertEquals("the origin must not be asked twice", afterFirst, origin.requests.get())
        }
    }

    /** The control for the reuse above: without a hash to check it against, a cached file is not
     * known to be the right one, so it is fetched again rather than trusted. */
    @Test
    fun `a cached apk is refetched when the release published no hash to check it by`() {
        Origin(payload).use { origin ->
            assertTrue(download(apk(origin, sha = null)) is UpdateDownload.Ready)
            val afterFirst = origin.requests.get()

            download(apk(origin, sha = null))

            assertTrue("the origin should be asked again", origin.requests.get() > afterFirst)
        }
    }

    @Test
    fun `progress is reported while the body arrives and ends at the full size`() {
        Origin(payload).use { origin ->
            val seen = mutableListOf<Long>()

            download(apk(origin)) { soFar, total ->
                seen += soFar
                assertEquals(payload.size.toLong(), total)
            }

            assertTrue("progress must be reported at all", seen.isNotEmpty())
            assertEquals(payload.size.toLong(), seen.last())
            assertEquals("progress must never go backwards", seen.sorted(), seen)
        }
    }

    /**
     * The sweep, and the lesson `EpgDownloader` already paid for: every path deletes its own file,
     * and none of them runs when the process dies mid-download. A fresh file must survive it - it
     * may be another download still writing.
     */
    @Test
    fun `stale leftovers are swept and fresh ones are left alone`() {
        cacheDirectory.mkdirs()
        val old = File(cacheDirectory, "abandoned.apk").apply {
            writeBytes(ByteArray(16))
            setLastModified(System.currentTimeMillis() - UpdateDownloader.STALE_AGE_MILLIS - 60_000)
        }
        val oldPart = File(cacheDirectory, "abandoned.apk.part").apply {
            writeBytes(ByteArray(16))
            setLastModified(System.currentTimeMillis() - UpdateDownloader.STALE_AGE_MILLIS - 60_000)
        }
        val fresh = File(cacheDirectory, "in-flight.apk.part").apply { writeBytes(ByteArray(16)) }

        UpdateDownloader(application).deleteStaleDownloads()

        assertFalse("a stale apk must go", old.exists())
        assertFalse("a stale part file must go", oldPart.exists())
        assertTrue("a file another download may still be writing must stay", fresh.exists())
    }

    private fun apksInCache(): List<File> =
        cacheDirectory.listFiles { f -> f.name.endsWith(".apk") }?.toList().orEmpty()
}
