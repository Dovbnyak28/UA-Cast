package com.uacastplayer.data.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Hex
import com.uacastplayer.update.ReleaseApk
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateDownloadCancellationTest {
    @Test fun cancellationAfterTheLastChunkCannotPublishAnApk() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(application.cacheDir, "updates")
        directory.deleteRecursively()
        val bytes = ByteArray(32) { 7 }
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(bytes.toResponseBody()).build()
        }.build()
        val download = async(Dispatchers.IO) {
            UpdateDownloader(application, httpClient = client).download(
                ReleaseApk("https://updates.test/cancelled.apk", bytes.size.toLong(), hash(bytes)),
            ) { _, _ ->
                writing.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            download.cancel()
            release.countDown()
            withTimeout(5_000) { download.join() }
            assertFalse("A cancelled buffered response must not publish an APK",
                directory.listFiles().orEmpty().any { it.name.endsWith(UpdateDownloader.APK_SUFFIX) })
            assertTrue("The cancelled writer must remove its partial file", directory.listFiles().orEmpty().isEmpty())
        } finally {
            release.countDown()
            download.cancel()
            withTimeout(5_000) { download.join() }
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    @Test fun aRetiringOwnerCannotDeleteAnotherOwnersReplacement() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(application.cacheDir, "updates")
        directory.deleteRecursively()
        val oldBytes = ByteArray(32) { 1 }
        val newBytes = ByteArray(32) { 2 }
        val requests = AtomicInteger()
        val firstWriting = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondRequest = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val number = requests.incrementAndGet()
            if (number == 2) secondRequest.countDown()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body((if (number == 1) oldBytes else newBytes).toResponseBody()).build()
        }.build()
        val oldOwner = UpdateDownloader(application, httpClient = client)
        val newOwner = UpdateDownloader(application, httpClient = client)
        val url = "https://updates.test/activity-replacement.apk"
        val first = async(Dispatchers.IO) {
            oldOwner.download(ReleaseApk(url, oldBytes.size.toLong(), sha256 = null)) { _, _ ->
                firstWriting.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        }
        var second: kotlinx.coroutines.Deferred<UpdateDownload>? = null
        try {
            assertTrue(firstWriting.await(5, TimeUnit.SECONDS))
            first.cancel()
            second = async(Dispatchers.IO) {
                newOwner.download(ReleaseApk(url, newBytes.size.toLong(), hash(newBytes)))
            }
            second.invokeOnCompletion { secondFinished.countDown() }
            if (secondRequest.await(300, TimeUnit.MILLISECONDS)) {
                assertTrue(secondFinished.await(5, TimeUnit.SECONDS))
            }
            val requestsBeforeRetirement = requests.get()
            releaseFirst.countDown()
            withTimeout(5_000) { first.join() }
            val result = withTimeout(5_000) { second.await() }
            assertTrue("Replacement must complete, got $result", result is UpdateDownload.Ready)
            assertTrue("The retiring owner's cleanup must not delete the replacement APK",
                (result as UpdateDownload.Ready).file.isFile)
            assertArrayEquals(newBytes, result.file.readBytes())
            assertEquals("Owners of the shared cache must serialize until old IO ends", 1,
                requestsBeforeRetirement)
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(UpdateDownloader.PART_SUFFIX) })
        } finally {
            releaseFirst.countDown()
            first.cancel()
            second?.cancel()
            withTimeout(5_000) { first.join(); second?.join() }
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    @Test fun aCancelledReaderCannotInterfereWithTheNextDownloadOnTheSameOwner() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(application.cacheDir, "updates")
        directory.deleteRecursively()
        val oldBytes = ByteArray(32) { 1 }
        val newBytes = ByteArray(32) { 2 }
        val requests = AtomicInteger()
        val firstWriting = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondRequest = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val number = requests.incrementAndGet()
            if (number == 2) secondRequest.countDown()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body((if (number == 1) oldBytes else newBytes).toResponseBody()).build()
        }.build()
        val downloader = UpdateDownloader(application, httpClient = client)
        val url = "https://updates.test/replaced.apk"
        // Legacy releases without a digest are valid and must still rely on the install signature gate.
        val first = async(Dispatchers.IO) {
            downloader.download(ReleaseApk(url, oldBytes.size.toLong(), sha256 = null)) { _, _ ->
                firstWriting.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(firstWriting.await(5, TimeUnit.SECONDS))
            first.cancel()
            val second = async(Dispatchers.IO) {
                downloader.download(ReleaseApk(url, newBytes.size.toLong(), sha256 = hash(newBytes)))
            }
            second.invokeOnCompletion { secondFinished.countDown() }
            // Bounded observation only. The old writer is deliberately blocked, not sleep-timed.
            if (secondRequest.await(300, TimeUnit.MILLISECONDS)) {
                assertTrue(secondFinished.await(5, TimeUnit.SECONDS))
            }
            val requestsBeforeRelease = requests.get()
            releaseFirst.countDown()
            withTimeout(5_000) { first.join() }
            val result = withTimeout(5_000) { second.await() }

            assertTrue("the replacement download must remain usable, got $result", result is UpdateDownload.Ready)
            assertTrue("the cancelled writer must not delete the replacement's published APK",
                (result as UpdateDownload.Ready).file.isFile)
            assertArrayEquals(newBytes, result.file.readBytes())
            assertEquals("cancellation must not release the download mutex before its writer ends", 1,
                requestsBeforeRelease)
            assertTrue("no partial download should survive", directory.listFiles().orEmpty().none {
                it.name.endsWith(UpdateDownloader.PART_SUFFIX)
            })
        } finally {
            releaseFirst.countDown()
            first.cancel()
            withTimeout(5_000) { first.join() }
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    private fun hash(bytes: ByteArray): String = Hex.encode(MessageDigest.getInstance("SHA-256").digest(bytes))
}
