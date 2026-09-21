package com.uacastplayer.data.cast

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyResourceRegistryTest {

    @Test
    fun `a duplicate live resource reuses its remux session instead of stopping it`() {
        val registry = ProxyResourceRegistry(OkHttpClient())
        val first = registry.startRemuxSession(
            resourceId = "same-resource",
            response = response(),
            segmentUrl = { resourceId, sequence -> "$resourceId-$sequence.ts" },
            isServerRunning = { true },
        )
        val second = registry.startRemuxSession(
            resourceId = "same-resource",
            response = response(),
            segmentUrl = { resourceId, sequence -> "$resourceId-$sequence.ts" },
            isServerRunning = { true },
        )

        try {
            assertSame(first, second)
            assertTrue(first != null && !first.hasEnded)
        } finally {
            registry.clearAll()
            first?.awaitStopped()
        }
    }

    private fun response(): Response = Response.Builder()
        .request(Request.Builder().url("https://origin.example/raw.ts").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ByteArray(0).toResponseBody())
        .build()
}
