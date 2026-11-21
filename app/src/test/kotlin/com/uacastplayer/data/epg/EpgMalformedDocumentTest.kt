package com.uacastplayer.data.epg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpgMalformedDocumentTest {
    @Test fun `HTTP 200 with malformed XML or truncated gzip fails safely and retains good snapshot`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "https://guide.example/xmltv"
        EpgSnapshotStore(context).save(Fingerprint.of(url), 1L, EpgData(EpgIndex(emptyList()), emptyMap()))
        listOf("<tv><programme".toByteArray(), byteArrayOf(0x1f, 0x8b.toByte(), 8, 0)).forEach { payload ->
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(payload.toResponseBody()).build()
            }.build()
            val repository = EpgRepository(context, httpClient = client)
            assertTrue(repository.loadFromUrl(url) is EpgOutcome.ReadError)
            assertNotNull(repository.restoreSnapshot(url))
        }
    }
}
