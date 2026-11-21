package com.uacastplayer.cast

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.PendingResults
import com.google.android.gms.common.api.Status
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.data.cast.ProxySourceDiagnostic
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    CastDirectNetworkOwnershipTest.BaseSessionShadow::class,
    CastDirectNetworkOwnershipTest.SessionShadow::class,
    CastDirectNetworkOwnershipTest.ClientShadow::class,
])
class CastDirectNetworkOwnershipTest {
    @Test
    fun `sender diagnosis does not displace the receiver sole stream connection`() = verifyDirectOwnership(1)

    @Test
    fun `thirty rapid switches leave the final receiver stream undisplaced`() = verifyDirectOwnership(30)

    @Test
    fun `cached incompatible codec still replaces the previous receiver media`() =
        verifyDirectOwnership(1, blocked = true)

    private fun verifyDirectOwnership(switches: Int, blocked: Boolean = false) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val constructor = CastSessionRepository::class.java.getDeclaredConstructor(
            Context::class.java, CoroutineDispatcher::class.java,
        ).apply { isAccessible = true }
        val repository = constructor.newInstance(context, Dispatchers.IO)
        val scope = field(repository, "scope") as CoroutineScope
        val session = CastSession::class.java.constructors.single { it.parameterCount == 6 }.newInstance(
            context, "test", "test-session", CastOptions.Builder().build(), null, null,
        ) as CastSession
        val client = RemoteMediaClient::class.java.constructors.single { it.parameterCount == 1 }
            .newInstance(null) as RemoteMediaClient
        Shadow.extract<SessionShadow>(session).client = client
        CastSessionRepository::class.java.getDeclaredField("currentSession")
            .apply { isAccessible = true }.set(repository, session)
        var receiverResponse: Response? = null
        try {
            SingleSlotOrigin().use { origin ->
                // The SDK is shadowed, but both origin connections are real HTTP sockets. A load
                // opens and holds the receiver stream; any diagnostic GET displaces it.
                var loads = 0
                Shadow.extract<ClientShadow>(client).onLoad = { request ->
                    loads++
                    // Superseded receiver loads do not fetch. Only the final receiver stream
                    // is held; any sender-origin request from any generation displaces it.
                    if (loads == switches) {
                        receiverResponse = OkHttpClient().newCall(
                            Request.Builder().url(checkNotNull(request.mediaInfo?.contentUrl)).build(),
                        ).execute()
                    }
                }
                repeat(switches) { index ->
                    val channel = CastChannel(index, "${origin.url}?channel=$index", "Live $index")
                    if (blocked) cacheBlockedCodec(repository, channel)
                    repository.setActiveChannel(channel)
                    runCurrent()
                }
                // Results from all older SDK loads are delivered only after the final switch.
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                assertEquals("A skipped load could leave the previous receiver media playing", switches, loads)
                if (blocked) {
                    assertEquals(
                        CodecIncompatibility.Video(VideoCodec.Mpeg2Video), repository.state.value.codecIncompatibility,
                    )
                } else {
                    assertEquals(CastLoadPhase.LOADED, repository.state.value.loadPhase)
                }

                assertFalse(
                    "The diagnostic GET displaced the receiver's one allowed origin connection",
                    origin.displacedReceiver.await(1, TimeUnit.SECONDS),
                )
            }
        } finally {
            receiverResponse?.close()
            scope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun cacheBlockedCodec(repository: CastSessionRepository, channel: CastChannel) {
        val coordinator = field(repository, "diagnosticCoordinator") as CastDiagnosticCoordinator
        coordinator.record(
            channel,
            ProxySourceDiagnostic(
                CastCompatibilityVerdict.IncompatibleVideo(VideoCodec.Mpeg2Video), TsSourceKind.RawTs,
            ),
        )
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    @Implements(Session::class, callThroughByDefault = false)
    class BaseSessionShadow

    @Implements(CastSession::class, callThroughByDefault = false)
    class SessionShadow {
        lateinit var client: RemoteMediaClient
        @Implementation fun getRemoteMediaClient(): RemoteMediaClient = client
    }

    @Implements(RemoteMediaClient::class, callThroughByDefault = false)
    class ClientShadow {
        var onLoad: (MediaLoadRequestData) -> Unit = {}
        var currentMediaStatus: MediaStatus? = null

        @Implementation fun getMediaStatus(): MediaStatus? = currentMediaStatus

        @Implementation fun load(request: MediaLoadRequestData): PendingResult<RemoteMediaClient.MediaChannelResult> {
            onLoad(request)
            return PendingResults.immediatePendingResult(object : RemoteMediaClient.MediaChannelResult {
                override fun getStatus(): Status = Status.RESULT_SUCCESS
                override fun getCustomData(): JSONObject? = null
                override fun getMediaError(): MediaError? = null
            })
        }
    }

    private class SingleSlotOrigin : Closeable {
        private val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        private val receiver = AtomicReference<Socket?>()
        val displacedReceiver = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/live"
        private val worker = thread(name = "cast-direct-single-slot-origin", isDaemon = true) {
            try {
                val first = server.accept()
                receiver.set(first)
                readHeaders(first)
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\n" +
                    "Transfer-Encoding: chunked\r\n\r\n"
                first.getOutputStream().apply { write(headers.toByteArray()); flush() }
                server.accept().use { second ->
                    readHeaders(second)
                    receiver.getAndSet(null)?.close()
                    displacedReceiver.countDown()
                    second.getOutputStream().apply {
                        write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            } catch (_: IOException) {
                // close() wakes the blocked accept/read.
            }
        }

        private fun readHeaders(socket: Socket) {
            socket.soTimeout = 3_000
            val reader = socket.getInputStream().bufferedReader()
            while (!reader.readLine().isNullOrEmpty()) {
                // Drain the GET request headers before responding.
            }
        }

        override fun close() {
            server.close()
            receiver.getAndSet(null)?.close()
            worker.join(3_000)
        }
    }
}
