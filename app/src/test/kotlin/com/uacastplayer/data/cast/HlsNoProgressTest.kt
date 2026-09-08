package com.uacastplayer.data.cast

import com.uacastplayer.proxy.HlsReplayProgress
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsNoProgressTest {
    @Test(timeout = 5_000) fun `segment failures after initial bytes terminate the committed HTTP response`() {
        verifyBoundedReplay(frozenManifest = false)
    }

    @Test(timeout = 5_000) fun `live manifest stuck at one sequence terminates without polling forever`() {
        verifyBoundedReplay(frozenManifest = true)
    }

    private fun verifyBoundedReplay(frozenManifest: Boolean) {
        var now = 0L
        var manifests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body: ByteArray
            val code: Int
            if (request.url.encodedPath.endsWith("m3u8")) {
                manifests++
                now += 30_000
                val sequence = if (frozenManifest) 1 else manifests
                body = "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:$sequence\n".plus(
                    "#EXTINF:1,\n/$sequence.ts\n",
                ).toByteArray()
                code = 200
            } else {
                code = if (manifests == 1) 200 else 403
                body = if (code == 200) ByteArray(188 * 3) { if (it % 188 == 0) 0x47 else 0 } else byteArrayOf()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        val stream = HlsFlattenedStream(
            client, "https://origin.example/live.m3u8", "test", null, { true },
            progress = HlsReplayProgress(nowMillis = { now }),
        )
        val output = ByteArrayOutputStream()
        var headers = 0
        assertTrue(stream.writeTo(output) { headers++ })
        assertEquals(1, headers)
        assertEquals(3, manifests)
        assertEquals(188 * 3, output.size())
    }
}
