package com.uacastplayer.dlna

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DlnaSessionLifecycleTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val device = DlnaDevice("Fixture TV", "https://renderer.example/control")
    // Interceptors append on IO threads while the assertions iterate; synchronizedList only
    // protects individual calls, not count/last iterators spanning a concurrent append.
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private var repository: DlnaSessionRepository? = null

    @After fun close() {
        repository?.stop()
        scope.cancel()
    }

    private fun create(
        response: (String, String) -> Int = { _, _ -> 200 },
        proxyClient: OkHttpClient = OkHttpClient(),
    ): DlnaSessionRepository {
        val soap = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.header("SOAPACTION").orEmpty().substringAfter('#').trim('"')
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            requests += action to body
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(response(action, body)).message("fixture").body("".toResponseBody()).build()
        }.build()
        return DlnaSessionRepository(
            ApplicationProvider.getApplicationContext<Context>(), scope = scope,
            localAddress = { "127.0.0.1" }, soapHttpClient = soap, proxyHttpClient = proxyClient,
        ).also { repository = it }
    }

    private suspend fun connected(repo: DlnaSessionRepository) = withTimeout(5_000) {
        repo.state.first { !it.isConnecting && it.connectedDevice != null }
    }

    @Test fun `failed repoint after Stop relinquishes renderer ownership`() = runBlocking {
        val repo = create(response = { action, body ->
            if (action == "SetAVTransportURI" && body.contains("Rejected")) 500 else 200
        })
        repo.connect(device, "https://origin/a.ts", "Accepted")
        connected(repo)
        repo.setActiveChannel("https://origin/b.ts", "Rejected")
        val failed = withTimeout(5_000) { repo.state.first { !it.isConnecting } }
        assertNull(failed.connectedDevice)
        assertEquals(device, failed.failedDevice)
        assertNull(failed.connectingDevice)
        assertTrue(requests.count { it.first == "Stop" } >= 2)
    }

    @Test fun `channel switch replaces pending initial connection`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = create(response = { action, body ->
            if (action == "SetAVTransportURI" && body.contains("First")) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            200
        })
        try {
            repo.connect(device, "https://origin/a.ts", "First")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(device, repo.state.value.connectingDevice)
            assertNull(repo.state.value.failedDevice)
            repo.setActiveChannel("https://origin/b.ts", "Latest")
        } finally {
            release.countDown()
        }
        connected(repo)
        assertTrue(requests.last { it.first == "SetAVTransportURI" }.second.contains("Latest"))
    }

    @Test fun `stop reaches pending renderer even if its Play reply has not arrived`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = create(response = { action, _ ->
            if (action == "Play") {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            200
        })
        try {
            repo.connect(device, "https://origin/a.ts", "First")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repo.stop()
        } finally {
            release.countDown()
        }
        withTimeout(5_000) { while (requests.count { it.first == "Stop" } < 2) delay(10) }
        assertNull(repo.state.value.connectedDevice)
        assertTrue(!repo.state.value.isConnecting)
    }

    @Test fun `provider headers travel from DLNA request through real proxy to origin`() = runBlocking {
        val upstreamRequests = CopyOnWriteArrayList<Request>()
        val upstream = OkHttpClient.Builder().addInterceptor { chain ->
            upstreamRequests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "video/mp2t")
                .body(ByteArray(188 * 3) { if (it % 188 == 0) 0x47 else 0 }.toResponseBody()).build()
        }.build()
        val repo = create(proxyClient = upstream)
        repo.connect(device, "https://origin/a.ts", "Headers", "RequiredAgent", "https://provider/")
        connected(repo)
        val envelope = requests.last { it.first == "SetAVTransportURI" }.second
        val url = envelope.substringAfter("<CurrentURI>").substringBefore("</CurrentURI>").replace("&amp;", "&")
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(it.body.bytes().isNotEmpty())
        }
        assertEquals("RequiredAgent", upstreamRequests.first().header("User-Agent"))
        assertEquals("https://provider/", upstreamRequests.first().header("Referer"))
    }
}
