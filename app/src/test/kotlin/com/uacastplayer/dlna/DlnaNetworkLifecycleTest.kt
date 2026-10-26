package com.uacastplayer.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DlnaNetworkLifecycleTest {
    private class QueuedDispatcher : CoroutineDispatcher() {
        @Volatile var paused = false
        private val tasks = ConcurrentLinkedQueue<Runnable>()

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = paused
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }

        fun resume() {
            paused = false
            while (true) (tasks.poll() ?: return).run()
        }
    }

    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val address = AtomicReference<String?>("127.0.0.1")
        val device = DlnaDevice("Fixture TV", "https://renderer.example/control")
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture").body("".toResponseBody()).build()
        }.build()
        val repository = DlnaSessionRepository(context, scope = scope,
            localAddress = { address.get() }, soapHttpClient = client)

        suspend fun connect(): ConnectivityManager.NetworkCallback {
            repository.connect(device, "https://origin.example/live.ts", "Fixture")
            withTimeout(5_000) {
                repository.state.first { it.connectedDevice != null && !it.isConnecting }
                // State is published before the monitoring callback is registered.
                while (shadowOf(manager).networkCallbacks.isEmpty()) delay(10)
            }
            return shadowOf(manager).networkCallbacks.single()
        }

        fun linkPropertiesChanged(callback: ConnectivityManager.NetworkCallback) {
            callback.onLinkPropertiesChanged(checkNotNull(manager.activeNetwork), LinkProperties())
        }

        override fun close() {
            dispatcher.resume()
            repository.stop()
            scope.cancel()
            client.connectionPool.evictAll()
        }
    }

    @Test fun `DHCP address change without Wi-Fi disconnect ends the obsolete proxy session`() = runBlocking {
        Fixture().use { fixture ->
            val callback = fixture.connect()
            fixture.address.set("127.0.0.2")
            fixture.linkPropertiesChanged(callback)

            assertEquals(DlnaConnectionState(), fixture.repository.state.value)
            assertTrue(shadowOf(fixture.manager).networkCallbacks.isEmpty())
        }
    }

    @Test fun `link properties change with the same address preserves playback`() = runBlocking {
        Fixture().use { fixture ->
            val callback = fixture.connect()
            val before = fixture.repository.state.value
            fixture.linkPropertiesChanged(callback)

            assertEquals(before, fixture.repository.state.value)
            assertTrue(shadowOf(fixture.manager).networkCallbacks.contains(callback))
        }
    }

    @Test fun `retired network callback cannot disconnect a replacement session`() = runBlocking {
        Fixture().use { fixture ->
            val retiredCallback = fixture.connect()
            fixture.repository.stop()
            fixture.address.set("127.0.0.2")
            val currentCallback = fixture.connect()
            val before = fixture.repository.state.value
            fixture.address.set("127.0.0.3")
            fixture.linkPropertiesChanged(retiredCallback)

            assertEquals(before, fixture.repository.state.value)
            assertTrue(shadowOf(fixture.manager).networkCallbacks.contains(currentCallback))
        }
    }

    @Test fun `address restored before a queued network check preserves the session`() = runBlocking {
        Fixture().use { fixture ->
            val callback = fixture.connect()
            val before = fixture.repository.state.value
            fixture.dispatcher.paused = true
            fixture.address.set(null)
            callback.onLost(checkNotNull(fixture.manager.activeNetwork))
            fixture.address.set("127.0.0.1")
            fixture.dispatcher.resume()

            assertEquals(before, fixture.repository.state.value)
            assertTrue(shadowOf(fixture.manager).networkCallbacks.contains(callback))
        }
    }
}
