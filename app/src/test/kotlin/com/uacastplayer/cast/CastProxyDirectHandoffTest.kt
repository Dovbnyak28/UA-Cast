package com.uacastplayer.cast

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.data.cast.ProxySourceDiagnostic
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    CastDirectNetworkOwnershipTest.BaseSessionShadow::class,
    CastDirectNetworkOwnershipTest.SessionShadow::class,
    CastDirectNetworkOwnershipTest.ClientShadow::class,
])
class CastProxyDirectHandoffTest {
    @Test fun `proxy remux closes the old origin before direct receiver load`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.startPreviousRemux()
                var loadedUrl: String? = null
                f.clientShadow.onLoad = { request ->
                    loadedUrl = request.mediaInfo?.contentUrl
                    val status = f.origin.newCall(Request.Builder().url(checkNotNull(loadedUrl)).build())
                        .execute().use { it.code }
                    assertEquals("Old proxy reader occupied the provider's sole connection", 200, status)
                }

                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))

                assertEquals(DIRECT_URL, loadedUrl)
                assertTrue("Previous remux response was not closed", f.liveBody.closed.get())
                assertEquals(CastDeliveryMode.Direct, f.repository.state.value.deliveryMode)
                assertEquals(2, f.originRequests.get())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `old proxy URL is no longer served after switching to direct`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                val previous = f.startPreviousRemux()
                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                assertStoppedUrl(previous)
                assertEquals("A retired proxy must not open another upstream", 1, f.originRequests.get())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `one slot model rejects direct while the previous producer is still alive`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.startPreviousRemux()
                assertFalse(f.liveBody.closed.get())
                f.origin.newCall(Request.Builder().url(DIRECT_URL).build()).execute().use {
                    assertEquals("Control must reject overlapping origin consumers", 403, it.code)
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `direct watchdog can restart a retired proxy for the new channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.configureWifi()
                f.startPreviousRemux()
                val loads = mutableListOf<String>()
                f.clientShadow.onLoad = { loads += checkNotNull(it.mediaInfo?.contentUrl) }
                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                assertTrue(f.liveBody.closed.get())
                f.setReceiverStatus(ReceiverStatus.BUFFERING)
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()

                assertEquals(2, loads.size)
                assertEquals(DIRECT_URL, loads.first())
                val fallback = URI(loads.last())
                assertEquals("192.168.1.50", fallback.host)
                assertTrue(fallback.path.startsWith("/hls/proxy-direct-handoff/"))
                assertEquals(CastDeliveryMode.Proxy, f.repository.state.value.deliveryMode)
                assertProxyServes(fallback)
                assertEquals("Fallback must open only its own origin", 2, f.originRequests.get())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `proxy to proxy replacement preserves its port and retires the old producer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.configureWifi()
                val previous = f.startPreviousRemux("192.168.1.50")
                val next = CastChannel(1, "https://origin.example/next.ts", "Next")
                (field(f.repository, "diagnosticCoordinator") as CastDiagnosticCoordinator).record(
                    next, ProxySourceDiagnostic(CastCompatibilityVerdict.Compatible, TsSourceKind.RawTs),
                )
                var loadedUrl: String? = null
                f.clientShadow.onLoad = { loadedUrl = it.mediaInfo?.contentUrl }

                f.repository.setActiveChannel(next)

                val replacement = URI(checkNotNull(loadedUrl))
                assertEquals(previous.port, replacement.port)
                assertTrue(replacement.path.startsWith("/hls/proxy-direct-handoff/"))
                assertTrue(
                    "A replaced producer must free its origin even when the server stays up", f.liveBody.closed.get(),
                )
                assertEquals(CastDeliveryMode.Proxy, f.repository.state.value.deliveryMode)
                assertEquals("Preparing a new proxy resource must not prefetch", 1, f.originRequests.get())
                assertProxyServes(replacement)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `direct handoff releases Chromecast foreground protection and wake lock`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.startPreviousRemux()
                val service = f.deliverServiceStart()
                val lock = serviceWakeLock(service)
                assertTrue(lock.isHeld)
                assertFalse(shadowOf(service).isForegroundStopped)

                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()

                assertTrue(shadowOf(service).isForegroundStopped)
                assertTrue(shadowOf(service).isStoppedBySelf)
                assertFalse("Direct playback no longer needs the sender wake lock", lock.isHeld)
                assertTrue(
                    "Foreground cleanup must not disconnect the Cast session",
                    f.repository.state.value.isSessionConnected,
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `direct handoff leaves another active DLNA owner protected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.startPreviousRemux()
                val service = f.deliverServiceStart()
                f.startDlnaServiceOwner()
                val lock = serviceWakeLock(service)

                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()

                val ownership = field(service, "ownership") as CastProxyOwnership
                assertEquals(listOf(CastProxyTarget.DLNA), ownership.activeTargets)
                assertFalse(shadowOf(service).isForegroundStopped)
                assertFalse(shadowOf(service).isStoppedBySelf)
                assertTrue("DLNA still needs the shared service wake lock", lock.isHeld)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a new load does not inherit the previous channel playing state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.startPreviousRemux()
                assertEquals(ReceiverStatus.PLAYING, f.repository.state.value.receiverStatus)
                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))

                assertEquals(CastLoadPhase.LOADING, f.repository.state.value.loadPhase)
                assertEquals(
                    "Old PLAYING is not evidence that the replacement is playing",
                    ReceiverStatus.IDLE, f.repository.state.value.receiverStatus,
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `old playing callbacks cannot silence the replacement watchdog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.configureWifi()
                val previous = f.startPreviousRemux()
                val loads = mutableListOf<String>()
                f.clientShadow.onLoad = { loads += checkNotNull(it.mediaInfo?.contentUrl) }
                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                // The actual callback path rejects this old content ID. Its previously stored
                // PLAYING must not remain in state and settle the new load without any evidence.
                f.dispatchStatus(previous.toString(), "PLAYING")
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()

                assertEquals("A stalled replacement must reach proxy fallback", 2, loads.size)
                assertEquals(CastDeliveryMode.Proxy, f.repository.state.value.deliveryMode)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `matching replacement playing callback settles direct playback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.configureWifi()
                f.startPreviousRemux()
                var loads = 0
                f.clientShadow.onLoad = { loads++ }
                f.repository.setActiveChannel(CastChannel(1, DIRECT_URL, "Direct"))
                f.dispatchStatus(DIRECT_URL, "PLAYING")
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()

                assertEquals("A current PLAYING must not be interrupted by proxy fallback", 1, loads)
                assertEquals(ReceiverStatus.PLAYING, f.repository.state.value.receiverStatus)
                assertEquals(CastDeliveryMode.Direct, f.repository.state.value.deliveryMode)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `old playing state cannot settle a new silent proxy load`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            Fixture().use { f ->
                f.configureWifi()
                val previous = f.startPreviousRemux("192.168.1.50")
                val next = CastChannel(1, "https://origin.example/next.ts", "Next")
                (field(f.repository, "diagnosticCoordinator") as CastDiagnosticCoordinator).record(
                    next, ProxySourceDiagnostic(CastCompatibilityVerdict.Compatible, TsSourceKind.RawTs),
                )
                f.repository.setActiveChannel(next)
                f.dispatchStatus(previous.toString(), "PLAYING")
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()

                assertTrue(
                    "A silent proxy load must enter recovery, not inherit PLAYING",
                    f.repository.state.value.isRecovering,
                )
                assertEquals(ReceiverStatus.IDLE, f.repository.state.value.receiverStatus)
                assertEquals(CastDeliveryMode.Proxy, f.repository.state.value.deliveryMode)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun assertProxyServes(uri: URI) {
        Socket("127.0.0.1", uri.port).use { socket ->
            socket.soTimeout = 3_000
            socket.getOutputStream().write(requestBytes(uri))
            assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 200"))
        }
    }

    private fun serviceWakeLock(service: CastProxyService): PowerManager.WakeLock {
        val locks = service.javaClass.getDeclaredMethod("getWakeLocks")
            .apply { isAccessible = true }.invoke(service)
        return field(checkNotNull(locks), "wakeLock") as PowerManager.WakeLock
    }

    private fun assertStoppedUrl(uri: URI) {
        val response = runCatching {
            Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 1_000
                socket.getOutputStream().write(requestBytes(uri))
                socket.getInputStream().read()
            }
        }
        response.fold(
            onSuccess = { assertEquals("Old URL returned response bytes after retirement", -1, it) },
            onFailure = { assertTrue("Expected refusal/reset, not a read timeout: $it", it is SocketException) },
        )
    }

    private class Fixture : AutoCloseable {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        val liveBody = LiveTsBody()
        val originRequests = AtomicInteger()
        // Real proxy sockets and a real remux reader, with an intercepted origin so slot ownership
        // is deterministic: any direct request while the old response is open gets HTTP 403.
        val origin = OkHttpClient.Builder().addInterceptor { chain ->
            originRequests.incrementAndGet()
            val old = chain.request().url.toString() == PROXY_URL
            val status = if (old || liveBody.closed.get()) 200 else 403
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test")
                .header("Content-Type", if (old) "video/mp2t" else "application/x-mpegurl")
                .body(if (old) liveBody else HLS_BODY.toResponseBody()).build()
        }.build()
        val repository: CastSessionRepository = CastSessionRepository::class.java.getDeclaredConstructor(
            Context::class.java, CoroutineDispatcher::class.java,
        ).apply { isAccessible = true }.newInstance(context, Dispatchers.IO)
        private val scope = field(repository, "scope") as CoroutineScope
        private val proxy = CastProxySession(context, origin)
        private val session = CastSession::class.java.constructors.single { it.parameterCount == 6 }.newInstance(
            context, "test", "handoff-session", CastOptions.Builder().build(), null, null,
        ) as CastSession
        private val client = RemoteMediaClient::class.java.constructors.single { it.parameterCount == 1 }
            .newInstance(null) as RemoteMediaClient
        val clientShadow: CastDirectNetworkOwnershipTest.ClientShadow = Shadow.extract(client)
        private var oldReceiverSocket: Socket? = null
        private var serviceController: ServiceController<CastProxyService>? = null
        private var serviceStartId = 0

        init {
            Shadow.extract<CastDirectNetworkOwnershipTest.SessionShadow>(session).client = client
            setField(repository, "proxy", proxy)
            setField(repository, "currentSession", session)
        }

        @Suppress("UNCHECKED_CAST")
        fun startPreviousRemux(host: String = "127.0.0.1"): URI {
            proxy.adoptToken("proxy-direct-handoff")
            proxy.beginPlaybackAttempt()
            val prepared = proxy.prepare(host, PROXY_URL, "Old", null, null, "TV").getOrThrow()
            setField(repository, "activeChannel", CastChannel(0, PROXY_URL, "Old"))
            (field(repository, "_state") as MutableStateFlow<CastPlaybackState>).value = CastPlaybackState(
                isSessionConnected = true, receiverStatus = ReceiverStatus.PLAYING,
                loadPhase = CastLoadPhase.LOADED, deliveryMode = CastDeliveryMode.Proxy,
            )
            val uri = URI(prepared.localUrl)
            oldReceiverSocket = Socket("127.0.0.1", uri.port).apply { getOutputStream().write(requestBytes(uri)) }
            assertTrue("Real remux reader did not start", liveBody.reading.await(3, TimeUnit.SECONDS))
            val server = field(proxy, "server") as ProxyServer
            assertTrue(server.wasRemuxed(prepared.resourceId))
            return uri
        }

        fun configureWifi() {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = checkNotNull(manager.activeNetwork)
            val capabilities = ShadowNetworkCapabilities.newInstance()
            shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(manager).setNetworkCapabilities(network, capabilities)
            // These network-configuration APIs exist on Android but are hidden from android.jar.
            val address = ReflectionHelpers.callConstructor(
                LinkAddress::class.java, ClassParameter.from(String::class.java, "192.168.1.50/24"),
            )
            val properties = LinkProperties()
            ReflectionHelpers.callInstanceMethod<Boolean>(
                properties, "addLinkAddress", ClassParameter.from(LinkAddress::class.java, address),
            )
            shadowOf(manager).setLinkProperties(network, properties)
        }

        @Suppress("UNCHECKED_CAST")
        fun setReceiverStatus(status: ReceiverStatus) {
            val state = field(repository, "_state") as MutableStateFlow<CastPlaybackState>
            state.value = state.value.copy(receiverStatus = status)
        }

        fun deliverServiceStart(): CastProxyService {
            val controller = serviceController ?: Robolectric.buildService(CastProxyService::class.java).create()
                .also { serviceController = it }
            controller.get().onStartCommand(checkNotNull(shadowOf(context).nextStartedService), 0, ++serviceStartId)
            return controller.get()
        }

        fun startDlnaServiceOwner() {
            CastProxyService.start(context, "Other stream", "Renderer", CastProxyTarget.DLNA)
            deliverServiceStart()
        }

        fun dispatchStatus(url: String, playerState: String) {
            val json = JSONObject().put("mediaSessionId", 1).put("playbackRate", 1)
                .put("playerState", playerState).put("currentTime", 0).put("supportedMediaCommands", 0)
                .put("media", JSONObject().put("contentId", url).put("streamType", "LIVE")
                    .put("contentType", "application/x-mpegurl"))
            clientShadow.currentMediaStatus = MediaStatus::class.java.getDeclaredConstructor(JSONObject::class.java)
                .apply { isAccessible = true }.newInstance(json)
            CastSessionRepository::class.java.getDeclaredMethod("handleRemoteMediaStatus", CastSession::class.java)
                .apply { isAccessible = true }.invoke(repository, session)
        }

        override fun close() {
            scope.cancel()
            proxy.stop()
            oldReceiverSocket?.close()
            shadowOf(Looper.getMainLooper()).idle()
            serviceController?.destroy()
            origin.connectionPool.evictAll()
            origin.dispatcher.executorService.shutdownNow()
        }
    }

    private class LiveTsBody : ResponseBody() {
        val reading = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val prefix = Buffer().write(ByteArray(188 * 800).apply {
            for (offset in indices step 188) {
                this[offset] = 0x47
                this[offset + 1] = 0x1F
                this[offset + 2] = 0xFF.toByte()
                this[offset + 3] = 0x10
            }
        })
        private val bytes = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (prefix.size > 0) return prefix.read(sink, byteCount)
                reading.countDown()
                while (!closed.get()) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5))
                return -1
            }
            override fun close() { closed.set(true) }
            override fun timeout() = Timeout.NONE
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source() = bytes
    }

    private companion object {
        const val PROXY_URL = "https://origin.example/old.ts"
        const val DIRECT_URL = "https://origin.example/new.m3u8"
        const val HLS_BODY = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment1.ts\n#EXTINF:6,\nsegment2.ts\n"

        fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)

        fun setField(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.set(owner, value)

        fun requestBytes(uri: URI): ByteArray =
            "GET ${uri.rawPath} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray()
    }
}
