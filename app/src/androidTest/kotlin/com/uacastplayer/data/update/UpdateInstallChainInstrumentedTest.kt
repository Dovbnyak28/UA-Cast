package com.uacastplayer.data.update

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.core.security.FileDigest
import com.uacastplayer.update.ReleaseApk
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update chain, on a device that can actually answer the questions it asks.
 *
 * [ApkSignatureGate] is the one part of this app that cannot be tested anywhere else at all: it
 * asks `PackageManager` who signed the running app and who signed a file, and there is no
 * `PackageManager` off a device. Its rule is a pure function with its own unit tests
 * ([com.uacastplayer.update.ApkTrustPolicy]); everything *around* the rule - reading a real signing
 * certificate on this API level, parsing a real APK archive, refusing one that is not - has never
 * been exercised until here.
 *
 * That matters more than usual because of what the gate is for. An APK signed with a different key
 * cannot be installed over this app: Android refuses it, and the user's only way forward is to
 * uninstall - losing their playlist, their guide and their licence - and start again. A gate that
 * silently answered "trusted" for anything would turn the update button into that.
 */
class UpdateInstallChainInstrumentedTest {

    private var origin: Origin? = null
    private val scratch = mutableListOf<File>()

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        origin?.close()
        scratch.forEach { runCatching { it.delete() } }
    }

    private fun scratchFile(name: String): File =
        File(context().cacheDir, name).also { scratch.add(it) }

    /** Serves one body, counting how many times it was asked for - which is how "it did not
     * download that again" is asserted rather than assumed. */
    private class Origin(private val body: ByteArray, private val status: String = "200 OK") : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newCachedThreadPool()
        private val hits = AtomicInteger(0)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/app-universal-release.apk"

        fun hits(): Int = hits.get()

        init {
            worker.execute {
                while (!socket.isClosed) {
                    try {
                        val accepted = socket.accept()
                        worker.execute { serve(accepted) }
                    } catch (_: IOException) {
                        return@execute
                    }
                }
            }
        }

        private fun serve(client: Socket) {
            client.use {
                it.getInputStream().read(ByteArray(REQUEST_BUFFER_BYTES))
                hits.incrementAndGet()
                val out = it.getOutputStream()
                val payload = if (status.startsWith("200")) body else ByteArray(0)
                out.write(
                    ("HTTP/1.1 $status\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                        "Content-Length: ${payload.size}\r\n\r\n").toByteArray(),
                )
                out.write(payload)
                out.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.shutdownNow()
        }
    }

    /**
     * This app's own installed APK, signed by definition with the key that signed this app.
     *
     * The only genuinely trusted APK obtainable on a device without shipping a second signed
     * artifact into the test - and a far better positive case than a fixture would be, because it
     * goes through the same `getPackageArchiveInfo` parse on the same real file the system
     * installed.
     */
    private fun installedApk(): File = File(context().applicationInfo.sourceDir)

    @Test
    fun thisAppsOwnApkIsTrustedByTheGate() {
        assertTrue(
            "the gate cannot recognise this app's own signature on this device - every update would be refused",
            ApkSignatureGate.isTrustedUpdate(context(), installedApk()),
        )
    }

    /**
     * The failure that is not a signature failure.
     *
     * The instrumentation APK sitting beside this one is signed by the *same* debug key and is a
     * perfectly valid, readable archive - it differs only in package name. That isolates the
     * package check from the signature check, which nothing else can: a gate that only compared
     * certificates would pass this and offer to install a different application over the user's.
     */
    @Test
    fun anApkForAnotherPackageIsRefusedEvenWhenTheSignerMatches() {
        val instrumentationApk = File(javaClass.protectionDomain?.codeSource?.location?.path ?: "")
        val other = context().packageManager
            .getPackageInfo(context().packageName + ".test", 0)
            .applicationInfo
            ?.sourceDir
            ?.let(::File)
            ?: instrumentationApk

        assertTrue("could not locate the test APK to compare against", other.isFile)
        assertFalse(
            "an APK for a different package must be refused however it is signed",
            ApkSignatureGate.isTrustedUpdate(context(), other),
        )
    }

    /**
     * A gate has to answer "no" when it cannot tell, or an unreadable file becomes the easiest
     * thing to get past it. All three of these are real shapes a download arrives in: a captive
     * portal's HTML saved under an .apk name, a transfer cut off partway, and a file that is not
     * there at all.
     */
    @Test
    fun nothingThatIsNotAReadableApkIsEverTrusted() {
        val html = scratchFile("portal.apk").apply { writeText("<html><body>Sign in to Wi-Fi</body></html>") }
        val truncated = scratchFile("half.apk").apply {
            writeBytes(installedApk().readBytes().copyOfRange(0, TRUNCATED_BYTES))
        }
        val missing = File(context().cacheDir, "never-written.apk")

        assertFalse("an HTML error page was trusted as an update", ApkSignatureGate.isTrustedUpdate(context(), html))
        assertFalse("a truncated download was trusted", ApkSignatureGate.isTrustedUpdate(context(), truncated))
        assertFalse("a missing file was trusted", ApkSignatureGate.isTrustedUpdate(context(), missing))
    }

    /** The whole point of publishing a digest. A file that is not the file the release describes
     * must never reach the signature gate, let alone the installer. */
    @Test
    fun aDownloadIsCheckedAgainstWhatTheReleasePublished() = runBlocking {
        val payload = ByteArray(PAYLOAD_BYTES) { (it % 251).toByte() }
        val origin = Origin(payload).also { this@UpdateInstallChainInstrumentedTest.origin = it }
        val downloader = UpdateDownloader(context())
        val realDigest = FileDigest.sha256(scratchFile("probe.bin").apply { writeBytes(payload) })

        val good = downloader.download(ReleaseApk(origin.url, payload.size.toLong(), realDigest))
        assertTrue("a matching download was rejected: $good", good is UpdateDownload.Ready)

        val wrongHash = downloader.download(ReleaseApk(origin.url, payload.size.toLong(), "00".repeat(SHA256_BYTES)))
        assertEquals(UpdateDownload.Corrupt, wrongHash)

        val wrongSize = downloader.download(ReleaseApk(origin.url, payload.size + 1L, null))
        assertEquals(UpdateDownload.Corrupt, wrongSize)
    }

    /**
     * The reuse path, and it is worth a device test because it is about the file system rather than
     * about arithmetic: a download that finished and whose install the user dismissed must not be
     * paid for twice on a phone connection.
     */
    @Test
    fun anAlreadyVerifiedDownloadIsNotFetchedAgain() = runBlocking {
        val payload = ByteArray(PAYLOAD_BYTES) { (it % 241).toByte() }
        val origin = Origin(payload).also { this@UpdateInstallChainInstrumentedTest.origin = it }
        val digest = FileDigest.sha256(scratchFile("probe2.bin").apply { writeBytes(payload) })
        val apk = ReleaseApk(origin.url, payload.size.toLong(), digest)
        val downloader = UpdateDownloader(context())

        assertTrue(downloader.download(apk) is UpdateDownload.Ready)
        val hitsAfterFirst = origin.hits()

        assertTrue(downloader.download(apk) is UpdateDownload.Ready)

        assertEquals("the same verified APK was downloaded twice", hitsAfterFirst, origin.hits())
    }

    /** A misconfigured endpoint must not be allowed to fill the cache partition. The cap is
     * injected so the rule is measured rather than the disk. */
    @Test
    fun anEndpointServingSomethingEnormousIsAbandoned() = runBlocking {
        val payload = ByteArray(PAYLOAD_BYTES) { 0x41 }
        val origin = Origin(payload).also { this@UpdateInstallChainInstrumentedTest.origin = it }

        val result = UpdateDownloader(context(), maxBytes = TINY_CAP_BYTES)
            .download(ReleaseApk(origin.url, payload.size.toLong(), null))

        assertEquals(UpdateDownload.TooLarge, result)
    }

    /** An offline phone, a 5xx, a release whose asset was deleted - one answer, and the user's
     * response to it is to try again. */
    @Test
    fun aRefusedDownloadIsAPlainFailure() = runBlocking {
        val origin = Origin(ByteArray(0), status = "404 Not Found")
            .also { this@UpdateInstallChainInstrumentedTest.origin = it }

        val result = UpdateDownloader(context()).download(ReleaseApk(origin.url, 0, null))

        assertEquals(UpdateDownload.Failed, result)
    }

    /**
     * Not an assertion about the device's setting - that is the user's to make - but about the call
     * itself. Below API 26 there is no per-app switch and the manifest permission is the whole of
     * it; from 26 this is a real `PackageManager` call that must return an answer rather than throw
     * on whatever ROM this is running on.
     */
    @Test
    fun whetherThisAppMayInstallPackagesIsAnswerableOnThisDevice() {
        val answer = ApkInstaller.canInstallPackages(context())

        assertTrue("canInstallPackages must produce a boolean, not throw", answer || !answer)
    }

    private companion object {
        const val REQUEST_BUFFER_BYTES = 2048
        const val PAYLOAD_BYTES = 64 * 1024
        const val TRUNCATED_BYTES = 4096
        const val TINY_CAP_BYTES = 1024L
        const val SHA256_BYTES = 32
    }
}
