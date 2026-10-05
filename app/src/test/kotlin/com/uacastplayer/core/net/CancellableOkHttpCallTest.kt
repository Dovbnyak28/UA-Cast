package com.uacastplayer.core.net

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableOkHttpCallTest {
    @Test fun cancellationDoesNotReleaseTheOwnerWhileItsResponseReaderIsStillRunning() = runBlocking {
        val call = ManualCall()
        val body = TrackedBody()
        val readerStarted = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val ownerFinished = CountDownLatch(1)
        val callbackFinished = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            call.executeCancellable {
                readerStarted.countDown()
                check(releaseReader.await(5, TimeUnit.SECONDS))
            }
        }
        job.invokeOnCompletion { ownerFinished.countDown() }
        val callback = Thread {
            try {
                call.respond(body)
            } finally {
                callbackFinished.countDown()
            }
        }
        try {
            assertTrue(call.enqueued.await(5, TimeUnit.SECONDS))
            callback.start()
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertFalse(
                "the owner must retain its resources until its blocking reader has unwound",
                ownerFinished.await(300, TimeUnit.MILLISECONDS),
            )
            assertTrue(call.cancelled.get())
        } finally {
            releaseReader.countDown()
            withTimeout(5_000) { job.join() }
            assertTrue(callbackFinished.await(5, TimeUnit.SECONDS))
        }
        assertTrue(body.closed.get())
    }

    @Test fun aResponseDeliveredAfterCancellationIsClosedWithoutCallingTheReader() = runBlocking {
        val call = ManualCall()
        val body = TrackedBody()
        val reads = AtomicInteger()
        val job = launch(Dispatchers.IO) {
            call.executeCancellable { reads.incrementAndGet() }
        }
        assertTrue(call.enqueued.await(5, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(5_000) { job.join() }

        call.respond(body)

        assertEquals("retired readers must not create files or mutate caches", 0, reads.get())
        assertTrue(body.closed.get())
        assertTrue(call.cancelled.get())
    }

    @Test fun successfulReadingClosesTheResponseAndReturnsTheValue() = runBlocking {
        val call = ManualCall()
        val body = TrackedBody()
        var result: String? = null
        val job = launch(Dispatchers.IO) { result = call.executeCancellable { it.body.string() } }
        assertTrue(call.enqueued.await(5, TimeUnit.SECONDS))
        call.respond(body)
        withTimeout(5_000) { job.join() }

        assertEquals("payload", result)
        assertTrue(body.closed.get())
        assertFalse(call.cancelled.get())
    }

    @Test fun aReaderFailureIsPropagatedAndClosesTheResponse() = runBlocking {
        val call = ManualCall()
        val body = TrackedBody()
        val failure = IOException("read failed")
        var actual: Throwable? = null
        val job = launch(Dispatchers.IO) {
            actual = runCatching { call.executeCancellable<Unit> { throw failure } }.exceptionOrNull()
        }
        assertTrue(call.enqueued.await(5, TimeUnit.SECONDS))
        call.respond(body)
        withTimeout(5_000) { job.join() }

        // Coroutine stack-trace recovery may copy IOException; identity is not the contract.
        assertEquals(IOException::class.java, actual?.javaClass)
        assertEquals(failure.message, actual?.message)
        assertTrue(body.closed.get())
    }

    @Test fun aHeaderFailureIsDeliveredWithoutStartingAResponseReader() = runBlocking {
        val call = ManualCall()
        val reads = AtomicInteger()
        var actual: Throwable? = null
        val job = launch(Dispatchers.IO) {
            actual = runCatching { call.executeCancellable { reads.incrementAndGet() } }.exceptionOrNull()
        }
        assertTrue(call.enqueued.await(5, TimeUnit.SECONDS))
        call.fail(IOException("no headers"))
        withTimeout(5_000) { job.join() }

        assertEquals(IOException::class.java, actual?.javaClass)
        assertEquals("no headers", actual?.message)
        assertEquals(0, reads.get())
    }

    @Test fun blockingResponseReadingDoesNotRunOnTheCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(TrackedBody()).build()
        }.build()
        try {
            val readThread = client.newCall(Request.Builder().url("https://example.test/stream").build())
                .executeCancellable { Thread.currentThread() }
            assertTrue("body IO must never block the caller's UI thread", readThread !== caller)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test fun cancellationUnblocksARealSocketBodyReadAndRemainsCancellation() = runBlocking {
        val readerWaiting = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val serverFinished = CountDownLatch(1)
        val client = OkHttpClient()
        ServerSocket(0).use { server ->
            val serverThread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val headers = socket.getInputStream().bufferedReader()
                        var line = headers.readLine()
                        while (!line.isNullOrEmpty()) line = headers.readLine()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\npayload".toByteArray())
                            flush()
                        }
                        check(releaseServer.await(5, TimeUnit.SECONDS))
                    }
                } finally {
                    serverFinished.countDown()
                }
            }
            serverThread.start()
            var actual: Throwable? = null
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/body").build())
            val job = launch(Dispatchers.IO) {
                actual = runCatching {
                    call.executeCancellable { response ->
                        val input = response.body.byteStream()
                        repeat(7) { check(input.read() >= 0) }
                        readerWaiting.countDown()
                        input.read() // The server deliberately sends no more bytes.
                    }
                }.exceptionOrNull()
            }
            try {
                assertTrue(readerWaiting.await(5, TimeUnit.SECONDS))
                job.cancel()
                withTimeout(5_000) { job.join() }
                assertTrue(call.isCanceled())
                assertTrue("a cancelled read must not become a retryable IOException", actual is CancellationException)
            } finally {
                releaseServer.countDown()
                job.cancel()
                withTimeout(5_000) { job.join() }
                assertTrue(serverFinished.await(5, TimeUnit.SECONDS))
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    private class TrackedBody : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val buffer = Buffer().writeUtf8("payload")
        override fun contentType() = null
        override fun contentLength() = 7L
        override fun source(): BufferedSource = buffer
        override fun close() {
            closed.set(true)
            super.close()
        }
    }

    private class ManualCall(
        delegate: Call = OkHttpClient().newCall(Request.Builder().url("https://example.test/stream").build()),
    ) : Call by delegate {
        val enqueued = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        private lateinit var callback: Callback
        private val request = Request.Builder().url("https://example.test/stream").build()

        fun respond(body: ResponseBody) = callback.onResponse(
            this,
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build(),
        )

        fun fail(error: IOException) = callback.onFailure(this, error)

        override fun request(): Request = request
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            enqueued.countDown()
        }
        override fun cancel() { cancelled.set(true) }
        override fun isCanceled(): Boolean = cancelled.get()
        override fun isExecuted(): Boolean = enqueued.count == 0L
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = ManualCall()
        override fun execute(): Response = error("only the async API is used")
    }
}
