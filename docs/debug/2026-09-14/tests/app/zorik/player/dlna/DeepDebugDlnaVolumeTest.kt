package app.zorik.player.dlna

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Opt-in diagnostic invariant test; production sources are not changed. */
@RunWith(RobolectricTestRunner::class)
class DeepDebugDlnaVolumeTest {
    @Test fun `two rejected volume commands must retain last confirmed renderer volume`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val setCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val soap = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val action = request.header("SOAPACTION").orEmpty().substringAfter('#').trim('"')
            if (action == "SetVolume" && setCalls.incrementAndGet() == 1) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (action == "SetVolume") 500 else 200).message("fixture")
                .body((if (action == "GetVolume") "<CurrentVolume>20</CurrentVolume>" else "").toResponseBody())
                .build()
        }.build()
        val repo = DlnaSessionRepository(
            ApplicationProvider.getApplicationContext<Context>(), scope = scope,
            localAddress = { "127.0.0.1" }, soapHttpClient = soap,
        )
        try {
            repo.connect(DlnaDevice("TV", "https://renderer.example/av", "https://renderer.example/volume"),
                "https://origin.example/live.ts", "Fixture")
            withTimeout(5_000) { repo.state.first { it.connectedDevice != null && !it.isConnecting } }
            assertEquals(20, repo.state.value.volume)
            repo.setVolume(40)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repo.setVolume(80)
            release.countDown()
            withTimeout(5_000) { repo.state.first { it.volume != 80 } }
            assertEquals(2, setCalls.get())
            assertEquals("Both SOAP requests were refused; the television is still at 20", 20, repo.state.value.volume)
        } finally {
            release.countDown()
            repo.stop()
            scope.cancel()
        }
    }
}
