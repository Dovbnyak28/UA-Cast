package com.uacastplayer.dlna

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DlnaVolumeLifecycleTest {
    private class Fixture : AutoCloseable {
        val actual = AtomicInteger(20)
        val setCalls = AtomicInteger()
        val getCalls = AtomicInteger()
        var set: (Int) -> Int = { actual.set(it); 200 }
        var get: () -> Int? = { actual.get() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.header("SOAPACTION").orEmpty().substringAfter('#').trim('"')
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            var reply = ""
            val code = when (action) {
                "SetVolume" -> {
                    setCalls.incrementAndGet()
                    set(body.substringAfter("<DesiredVolume>").substringBefore("</DesiredVolume>").toInt())
                }
                "GetVolume" -> {
                    getCalls.incrementAndGet()
                    val volume = get()
                    reply = volume?.let { "<CurrentVolume>$it</CurrentVolume>" }.orEmpty()
                    if (volume == null) 500 else 200
                }
                else -> 200
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(reply.toResponseBody()).build()
        }.build()
        val repo = DlnaSessionRepository(ApplicationProvider.getApplicationContext<Context>(),
            scope = scope, localAddress = { "127.0.0.1" }, soapHttpClient = client)
        suspend fun connect() {
            repo.connect(DlnaDevice("TV", "https://renderer.example/av", "https://renderer.example/volume"),
                "https://origin.example/live.ts", "Fixture")
            withTimeout(5_000) { repo.state.first { it.connectedDevice != null && !it.isConnecting } }
            assertEquals(20, repo.state.value.volume)
        }
        suspend fun awaitCorrection(requested: Int): Int? = withTimeout(5_000) {
            repo.state.first { it.volume != requested }.volume
        }
        override fun close() {
            repo.stop()
            scope.cancel()
            client.connectionPool.evictAll()
        }
    }

    @Test fun `two rejections restore actual volume not earlier optimistic value`() = rapidCommands(false)
    @Test fun `accepted then rejected command reads accepted volume back`() = rapidCommands(true)

    private fun rapidCommands(firstAccepted: Boolean) = runBlocking {
        Fixture().use { fixture ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            fixture.set = { requested ->
                if (fixture.setCalls.get() == 1) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    if (firstAccepted) { fixture.actual.set(requested); 200 } else 500
                } else 500
            }
            try {
                fixture.connect()
                fixture.repo.setVolume(40)
                assertTrue(started.await(5, TimeUnit.SECONDS))
                fixture.repo.setVolume(80)
                release.countDown()
                assertEquals(if (firstAccepted) 40 else 20, fixture.awaitCorrection(80))
                assertEquals(2, fixture.setCalls.get())
            } finally { release.countDown() }
        }
    }

    @Test fun `renderer clamping wins over requested volume`() = runBlocking {
        Fixture().use { fixture ->
            fixture.set = { fixture.actual.set(it.coerceAtMost(50)); 200 }
            fixture.connect()
            fixture.repo.setVolume(80)
            assertEquals(50, fixture.awaitCorrection(80))
        }
    }

    @Test fun `lost SetVolume reply can still mean command was applied`() = runBlocking {
        Fixture().use { fixture ->
            fixture.set = { fixture.actual.set(75); throw IOException("reply lost after apply") }
            fixture.connect()
            fixture.repo.setVolume(80)
            assertEquals(75, fixture.awaitCorrection(80))
        }
    }

    @Test fun `failed readback reports unknown instead of inventing a volume`() = runBlocking {
        Fixture().use { fixture ->
            fixture.connect()
            fixture.get = { null }
            fixture.repo.setVolume(80)
            assertNull(fixture.awaitCorrection(80))
        }
    }

    @Test fun `disconnect during volume read cannot resurrect renderer state`() = runBlocking {
        Fixture().use { fixture ->
            fixture.connect()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            fixture.get = {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                finished.countDown()
                70
            }
            try {
                fixture.repo.setVolume(80)
                assertTrue(started.await(5, TimeUnit.SECONDS))
                fixture.repo.stop()
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(DlnaConnectionState(), fixture.repo.state.value)
            } finally { release.countDown() }
        }
    }
}
