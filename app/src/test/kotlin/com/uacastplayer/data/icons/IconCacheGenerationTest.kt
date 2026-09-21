package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconCacheGenerationTest {
    @Test fun obsoleteMissMustNotOverwriteSuccessfulResolutionAfterInvalidation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val count = AtomicInteger()
        val listener = ServerSocket(0)
        val workers = Executors.newCachedThreadPool()
        workers.execute {
            repeat(2) {
                val socket = listener.accept()
                val number = count.incrementAndGet()
                workers.execute {
                    socket.use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                        if (number == 1) {
                            firstStarted.countDown()
                            check(releaseFirst.await(10, TimeUnit.SECONDS))
                        }
                        val bytes = if (number == 1) "old failure".toByteArray() else {
                            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
                        }
                        val code = if (number == 1) "503 Service Unavailable" else "200 OK"
                        client.getOutputStream().apply {
                            val headers = "HTTP/1.1 $code\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            write(headers.toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                }
            }
        }
        val repository = IconRepository(app)
        val url = "http://127.0.0.1:${listener.localPort}/audit-${System.nanoTime()}.png"
        val old = async(Dispatchers.Default) { repository.resolveIconFile(url, null, null) }
        try {
            assertTrue("old request must already be in flight", firstStarted.await(5, TimeUnit.SECONDS))
            repository.retryTransientFailures()
            val new = repository.resolveIconFile(url, null, null)
            assertNotNull("control: newer HTTP 200 published an icon", new)
            releaseFirst.countDown()
            assertNull("older HTTP 503 resolves as a miss", old.await())
            assertTrue("the successfully downloaded icon is still on disk", checkNotNull(new).isFile)
            val afterOldCompleted = repository.resolveIconFile(url, null, null)
            repository.invalidateMemoryCache()
            val afterAnotherInvalidation = repository.resolveIconFile(url, null, null)
            assertNotNull("control: an extra eviction reveals the valid disk icon", afterAnotherInvalidation)
            println("AUDIT icon memoryHit=$afterOldCompleted diskFileExists=${new.isFile}")
            assertNotNull("A stale failed request must not hide a newer successfully cached logo", afterOldCompleted)
        } finally {
            releaseFirst.countDown()
            old.cancel()
            listener.close()
            workers.shutdownNow()
        }
    }
}
