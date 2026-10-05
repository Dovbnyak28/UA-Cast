package com.uacastplayer.data.epg

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.CoroutineContext

class EpgDownloadHandoffTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cancellationWhileReturningToTheCallerDeletesTheUnclaimedDownload() {
        val directory = folder.newFolder()
        val caller = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + caller)
        var returned = false
        val client = client()
        try {
            val job = scope.launch {
                EpgDownloader(client, directory, Dispatchers.IO).download("https://epg.test/guide.xml")
                returned = true
            }
            caller.next().run() // Enter download, then suspend on IO.
            val delivery = caller.next() // Complete bytes are on disk; result not accepted yet.
            assertEquals(1, directory.listFiles().orEmpty().size)
            job.cancel()
            delivery.run()
            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled)
            assertTrue(!returned)
            assertEquals("no caller received ownership of this file", 0, directory.listFiles().orEmpty().size)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test fun normalDeliveryTransfersTheFileToTheCallerWithoutDeletingIt() {
        val directory = folder.newFolder()
        val caller = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + caller)
        val client = client()
        var result: EpgDownloadResult? = null
        try {
            val job = scope.launch {
                result = EpgDownloader(client, directory, Dispatchers.IO).download("https://epg.test/guide.xml")
            }
            caller.next().run()
            caller.next().run()

            assertTrue(job.isCompleted)
            val file = (result as EpgDownloadResult.Success).documentFile
            assertTrue(file.isFile)
            assertEquals("<tv/>", file.readText())
            assertTrue(file.delete())
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private fun client() = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body("<tv/>".toResponseBody()).build()
    }.build()

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun next(): Runnable = requireNotNull(queue.poll(5, TimeUnit.SECONDS)) { "missing coroutine dispatch" }
    }
}
